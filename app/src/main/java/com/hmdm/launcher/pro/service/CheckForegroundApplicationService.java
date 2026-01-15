package com.hmdm.launcher.pro.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.hmdm.launcher.Const;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Service to check the foreground application and block restricted apps (e.g.
 * Settings).
 */
public class CheckForegroundApplicationService extends Service {

    private boolean running = false;
    private Thread workerThread;
    private UsageStatsManager usageStatsManager;

    @Override
    public void onCreate() {
        super.onCreate();
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
    }

    @SuppressLint("LongLogTag")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!running) {
            running = true;
            workerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        checkForegroundApp();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            workerThread.start();
        }
        return START_STICKY;
    }

    @SuppressLint("LongLogTag")
    private void checkForegroundApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (usageStatsManager == null) {
                return;
            }

            String currentApp = getForegroundApp();

            if (currentApp != null) {
                if (Const.SETTINGS_PACKAGE_NAME.equals(currentApp)) {
                    // Double check to avoid false positives during rotation or other transient
                    // states
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    String confirmedApp = getForegroundApp();
                    if (Const.SETTINGS_PACKAGE_NAME.equals(confirmedApp)) {
                        Intent intent = new Intent(this, com.hmdm.launcher.ui.MainActivity.class);
                        intent.setAction(Const.ACTION_HIDE_SCREEN);
                        intent.putExtra(Const.PACKAGE_NAME, currentApp);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                }
            }
        }
    }

    private String getForegroundApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            long time = System.currentTimeMillis();
            // Query events for the last minute to be safe
            android.app.usage.UsageEvents usageEvents = usageStatsManager.queryEvents(time - 1000 * 60, time);

            if (usageEvents == null) {
                return null;
            }

            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            String currentApp = null;

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    currentApp = event.getPackageName();
                }
            }
            return currentApp;
        }
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
