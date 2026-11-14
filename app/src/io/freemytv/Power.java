package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public final class Power {
    private Power() {}

    public static void showDialog(Context context) {
        if (context == null) return;
        if (context instanceof Activity && ((Activity) context).isFinishing()) return;

        CharSequence[] items = new CharSequence[] {
            "⚡  " + Ui.tr("Power off"),
            "🔄  " + Ui.tr("Restart")
        };

        AlertDialog dialog = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(Ui.tr("Power"))
            .setItems(items, (d, which) -> {
                d.dismiss();
                if (which == 0) {
                    shutdown(context);
                } else if (which == 1) {
                    reboot(context);
                }
            })
            .setNegativeButton(Ui.tr("Cancel"), (d, which) -> d.dismiss())
            .create();

        dialog.show();
    }

    public static void shutdown(Context context) {
        new Thread(() -> {
            // 1. Try root commands
            if (exec("su", "-c", "reboot -p") || exec("su", "-c", "svc power shutdown")
                    || exec("su", "-c", "setprop sys.powerctl shutdown")) {
                return;
            }

            // 2. Try direct shell commands (many TV boxes allow reboot from shell)
            if (exec("sh", "-c", "reboot -p") || exec("sh", "-c", "svc power shutdown")
                    || exec("sh", "-c", "setprop sys.powerctl shutdown")) {
                return;
            }

            // 3. Try standard Android shutdown intent
            try {
                Intent intent = new Intent("android.intent.action.ACTION_REQUEST_SHUTDOWN");
                intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Throwable ignored) {}

            try {
                Intent intent = new Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN");
                intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            } catch (Throwable ignored) {}

            // 4. Fallback: simulate Power key (sleep/standby)
            exec("input", "keyevent", "26");
        }).start();
    }

    public static void reboot(Context context) {
        new Thread(() -> {
            // 1. Try root commands
            if (exec("su", "-c", "reboot") || exec("su", "-c", "svc power reboot")
                    || exec("su", "-c", "setprop sys.powerctl reboot")) {
                return;
            }

            // 2. Try direct shell commands
            if (exec("sh", "-c", "reboot") || exec("sh", "-c", "svc power reboot")
                    || exec("sh", "-c", "setprop sys.powerctl reboot")) {
                return;
            }

            // 3. Try PowerManager
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    pm.reboot(null);
                    return;
                }
            } catch (Throwable ignored) {}

            // 4. Try broadcast intent
            try {
                Intent intent = new Intent(Intent.ACTION_REBOOT);
                intent.putExtra("nowait", 1);
                intent.putExtra("interval", 1);
                intent.putExtra("window", 0);
                context.sendBroadcast(intent);
            } catch (Throwable ignored) {}
        }).start();
    }

    private static boolean exec(String... cmd) {
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 1500) {
                try {
                    return p.exitValue() == 0;
                } catch (IllegalThreadStateException e) {
                    Thread.sleep(100);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
