/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.corvus;

import static android.view.DisplayCutout.BOUNDS_POSITION_LEFT;
import static android.view.DisplayCutout.BOUNDS_POSITION_RIGHT;

import android.Manifest;
import android.content.Context;
import android.content.res.Resources;
import android.content.Intent;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.fingerprint.FingerprintManager;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.IActivityManager;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.provider.Settings;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.DisplayInfo;
import com.android.internal.R;

import java.util.List;

import com.android.internal.statusbar.IStatusBarService;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Some custom utilities
 */
public class CorvusUtils {

    private static OverlayManager mOverlayService;

    public static final String INTENT_SCREENSHOT = "action_take_screenshot";
    public static final String INTENT_REGION_SCREENSHOT = "action_take_region_screenshot";

    private static final String TAG = "CorvusUtils";

    private static final boolean DEBUG = false;

    private static final int NO_CUTOUT = -1;

    public static void switchScreenOff(Context ctx) {
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm!= null) {
            pm.goToSleep(SystemClock.uptimeMillis());
        }
    }

    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        Network[] networks = cm.getAllNetworks();
        for (int i = 0; i < networks.length; i++) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(networks[i]);
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return false;
            }
        }
        return true;
    }

    // Check to see if Wifi is connected
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = null;
        if (cm != null) {
            activeNetwork = cm.getActiveNetworkInfo();
        }
        NetworkInfo wifi = null;
        if (cm != null) {
            wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        }
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting() && wifi.isConnected();
    }

    // Check to see if Mobile data is connected
    public static boolean isMobileConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = null;
        if (cm != null) {
            activeNetwork = cm.getActiveNetworkInfo();
        }
        NetworkInfo mobile = null;
        if (cm != null) {
            mobile = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        }
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting() && mobile.isConnected();
    }

    // Check to see if device supports the Fingerprint scanner
    public static boolean hasFingerprintSupport(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected());
    }

    // Check to see if device not only supports the Fingerprint scanner but also if is enrolled
    public static boolean hasFingerprintEnrolled(Context context) {
        FingerprintManager fingerprintManager = (FingerprintManager) context.getSystemService(Context.FINGERPRINT_SERVICE);
        return context.getApplicationContext().checkSelfPermission(Manifest.permission.USE_FINGERPRINT) == PackageManager.PERMISSION_GRANTED &&
                (fingerprintManager != null && fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints());
    }

    // Check to see if device has a camera
    public static boolean hasCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    // Check to see if device supports NFC
    public static boolean hasNFC(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC);
    }

    // Check to see if device supports Wifi
    public static boolean hasWiFi(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    // Check to see if device supports Bluetooth
    public static boolean hasBluetooth(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    // Check to see if device supports an alterative ambient display package
    public static boolean hasAltAmbientDisplay(Context context) {
        return context.getResources().getBoolean(com.android.internal.R.bool.config_alt_ambient_display);
    }

    public static boolean deviceHasFlashlight(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    public static void toggleCameraFlash() {
        final IStatusBarService service = IStatusBarService.Stub.asInterface(
                        ServiceManager.getService("statusbar"));
        if (service != null) {
            try {
                service.toggleCameraFlash();
            } catch (RemoteException e) {
                // do nothing.
            }
        }
    }

    public static void sendKeycode(int keycode) {
        long when = SystemClock.uptimeMillis();
        final KeyEvent evDown = new KeyEvent(when, when, KeyEvent.ACTION_DOWN, keycode, 0,
                0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD);
        final KeyEvent evUp = KeyEvent.changeAction(evDown, KeyEvent.ACTION_UP);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evDown,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        });
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputManager.getInstance().injectInputEvent(evUp,
                        InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
            }
        }, 20);
    }

    public static void takeScreenshot(boolean full) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            // Do nothing
        }
        IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        try {
            wm.sendCustomAction(new Intent(full? INTENT_SCREENSHOT : INTENT_REGION_SCREENSHOT));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public static boolean deviceHasCompass(Context ctx) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        return sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
    }

    public static void restartSystemUi(Context context) {
        new RestartSystemUiTask(context).execute();
    }

    public static void showSystemUiRestartDialog(Context context) {
        new AlertDialog.Builder(context)
                .setTitle(R.string.systemui_restart_title)
                .setMessage(R.string.systemui_restart_message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        restartSystemUi(context);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static class RestartSystemUiTask extends AsyncTask<Void, Void, Void> {
        private Context mContext;

        public RestartSystemUiTask(Context context) {
            super();
            mContext = context;
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ActivityManager am =
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                IActivityManager ams = ActivityManager.getService();
                for (ActivityManager.RunningAppProcessInfo app: am.getRunningAppProcesses()) {
                    if ("com.android.systemui".equals(app.processName)) {
                        ams.killApplicationProcess(app.processName, app.uid);
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    // Check to see if a package is installed
    public static boolean isPackageInstalled(Context context, String pkg, boolean ignoreState) {
        if (pkg != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
                if (!pi.applicationInfo.enabled && !ignoreState) {
                    return false;
                }
            } catch (NameNotFoundException e) {
                return false;
            }
        }

        return true;
    }

    public static boolean isPackageInstalled(Context context, String pkg) {
        return isPackageInstalled(context, pkg, true);
    }

    // Check if device has a notch
    public static boolean hasNotch(Context context) {
        String displayCutout = context.getResources().getString(R.string.config_mainBuiltInDisplayCutout);
        boolean maskDisplayCutout = context.getResources().getBoolean(R.bool.config_maskMainBuiltInDisplayCutout);
        boolean displayCutoutExists = (!TextUtils.isEmpty(displayCutout) && !maskDisplayCutout);
        return displayCutoutExists;
    }

    public static int getCutoutType(Context context) {
        final DisplayInfo info = new DisplayInfo();
        context.getDisplay().getDisplayInfo(info);
        final DisplayCutout cutout = info.displayCutout;
        if (cutout == null) {
            if (DEBUG) Log.v(TAG, "noCutout");
            return NO_CUTOUT;
        }
        final Point displaySize = new Point();
        context.getDisplay().getRealSize(displaySize);
        List<Rect> cutOutBounds = cutout.getBoundingRects();
        if (cutOutBounds != null) {
            for (Rect cutOutRect : cutOutBounds) {
                if (DEBUG) Log.v(TAG, "cutout left= " + cutOutRect.left);
                if (DEBUG) Log.v(TAG, "cutout right= " + cutOutRect.right);
                if (cutOutRect.left == 0 && cutOutRect.right > 0) {  //cutout is located on top left
                    if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_LEFT);
                    return BOUNDS_POSITION_LEFT;
                } else if (cutOutRect.right == displaySize.x && (displaySize.x - cutOutRect.left) > 0) {  //cutout is located on top right
                    if (DEBUG) Log.v(TAG, "cutout position= " + BOUNDS_POSITION_RIGHT);
                    return BOUNDS_POSITION_RIGHT;
                }
            }
        }
        return NO_CUTOUT;
    }

    /**
     * Returns whether the device is voice-capable (meaning, it is also a phone).
     */
    public static boolean isVoiceCapable(Context context) {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephony != null && telephony.isVoiceCapable();
    }

    // Method to detect whether an overlay is enabled or not
    public static boolean isOverlayEnabled(String packageName) {
        mOverlayService = new OverlayManager();
        try {
            List<OverlayInfo> infos = mOverlayService.getOverlayInfosForTarget("android",
                    UserHandle.myUserId());
            for (int i = 0, size = infos.size(); i < size; i++) {
                if (infos.get(i).packageName.equals(packageName)) {
                    return infos.get(i).isEnabled();
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static class OverlayManager {
        private final IOverlayManager mService;

        public OverlayManager() {
            mService = IOverlayManager.Stub.asInterface(
                    ServiceManager.getService(Context.OVERLAY_SERVICE));
        }

        public void setEnabled(String pkg, boolean enabled, int userId)
                throws RemoteException {
            mService.setEnabled(pkg, enabled, userId);
        }

        public List<OverlayInfo> getOverlayInfosForTarget(String target, int userId)
                throws RemoteException {
            return mService.getOverlayInfosForTarget(target, userId);
        }
    }

    public static int getQSColumnsPortrait(Context context, int resourceCount) {
        final int QS_COLUMNS_MIN = 2;
        int value = QS_COLUMNS_MIN;
            value = Settings.System.getIntForUser(
                    context.getContentResolver(), "qs_layout_columns",
                    resourceCount, UserHandle.USER_CURRENT);
        return Math.max(QS_COLUMNS_MIN, value);
    }
    
    public static int getQSColumnsLandscape(Context context, int resourceCount) {
        final int QS_COLUMNS_MIN = 4;
        int value = QS_COLUMNS_MIN;
            value = Settings.System.getIntForUser(
                    context.getContentResolver(), "qs_layout_columns_landscape",
                    resourceCount, UserHandle.USER_CURRENT);
        return Math.max(QS_COLUMNS_MIN, value);
    }

    public static int getQuickQSColumnsPortrait(Context context, int resourceCount) {
        return getQSColumnsPortrait(context, resourceCount);
    }

    public static int getQuickQSColumnsLandscape(Context context, int resourceCount) {
        return getQSColumnsLandscape(context, resourceCount);
    }

    public static boolean getQSTileLabelHide(Context context) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.QS_TILE_LABEL_HIDE,
                0, UserHandle.USER_CURRENT) != 0;
    }

    public static boolean getQSTileVerticalLayout(Context context, int defaultValue) {
        return Settings.System.getIntForUser(context.getContentResolver(),
                Settings.System.QS_TILE_VERTICAL_LAYOUT,
                defaultValue, UserHandle.USER_CURRENT) != 0;
    }
}
