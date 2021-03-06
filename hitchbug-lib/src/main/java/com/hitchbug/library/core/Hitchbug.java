package com.hitchbug.library.core;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.hitchbug.library.core.database.CrashRecord;
import com.hitchbug.library.core.database.SherlockDatabaseHelper;
import com.hitchbug.library.core.investigation.AppInfo;
import com.hitchbug.library.core.investigation.AppInfoProvider;
import com.hitchbug.library.core.investigation.Crash;
import com.hitchbug.library.core.investigation.CrashAnalyzer;
import com.hitchbug.library.core.investigation.CrashReporter;
import com.hitchbug.library.core.investigation.DefaultAppInfoProvider;
import com.hitchbug.library.util.SendCrashDetails;

import org.json.JSONArray;

import java.util.List;

public class Hitchbug {

    public static String APP_KEY = "";

    private static final String TAG = Hitchbug.class.getSimpleName();
    private static Hitchbug instance;
    private final SherlockDatabaseHelper database;
    private final CrashReporter crashReporter;
    private AppInfoProvider appInfoProvider;

    private Hitchbug(Context context) {
        database = new SherlockDatabaseHelper(context);
        crashReporter = new CrashReporter(context);
        appInfoProvider = new DefaultAppInfoProvider(context);
    }

    public static void init(Application application, AppInfo appInfo) {

        Log.d(TAG, "Initializing Hitchbug...");
        instance = new Hitchbug(application);

        crashHandler(application, appInfo);

        final Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                analyzeAndReportCrash(throwable);
                handler.uncaughtException(thread, throwable);
            }
        });
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static Hitchbug getInstance() {
        if (!isInitialized()) {
            throw new HitchbugNotInitializedException();
        }
        Log.d(TAG, "Returning existing instance...");
        return instance;
    }

    public List<Crash> getAllCrashes() {
        return getInstance().database.getCrashes();
    }

    private static void analyzeAndReportCrash(Throwable throwable) {
        Log.d(TAG, "Analyzing Crash...");
        CrashAnalyzer crashAnalyzer = new CrashAnalyzer(throwable);
        Crash crash = crashAnalyzer.getAnalysis();
        int crashId = instance.database.insertCrash(CrashRecord.createFrom(crash));
        crash.setId(crashId);
        //instance.crashReporter.report(new CrashViewModel(crash));
        Log.d(TAG, "Crash analysis completed!");
    }

    private static void crashHandler(Application application, AppInfo appInfo) {

        SherlockDatabaseHelper database = new SherlockDatabaseHelper(application);

        // check if t crash list is contains data
        if (database.getCrashes().size() != 0) {

            JSONArray crashes = database.getCrashesAsJson();

            new SendCrashDetails(database.getCrashes().get(0), appInfo, result -> {
                if (result.contains("201")) {
                    // remove crashes
                    database.deleteFromTable();
                }
            }).execute(crashes.toString());
        }

    }

    public static void setAppInfoProvider(AppInfoProvider appInfoProvider) {
        getInstance().appInfoProvider = appInfoProvider;
    }

    public AppInfoProvider getAppInfoProvider() {
        return getInstance().appInfoProvider;
    }

    public static class Builder {

        public String packageName;
        public String versionName;
        public int versionCode;

        private String app_key;
        private Context applicationContext;
        @Nullable
        private Application application;

        public Builder(@Nullable Application application, String app_key, AppInfo appInfo) {
            this.application = application;
            this.app_key = app_key;

            APP_KEY = this.app_key;

            init(application, new AppInfo(appInfo.email, appInfo.packageName, appInfo.applicationId, appInfo.versionName, appInfo.versionCode));
        }

        public Hitchbug build() {
            return instance;
        }

    }
}
