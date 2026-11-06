package com.amountqr.app;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PaymentListener extends NotificationListenerService implements TextToSpeech.OnInitListener {

    static final Pattern AMOUNT = Pattern.compile(
            "(?:\u20B9|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE);

    static final String[] RECEIVED = {"received", "paid you", "credited", "sent you"};
    static final String[] IGNORE = {"debited", "request", "you paid", "paid to", "sent to", "failed", "otp"};

    TextToSpeech tts;
    boolean ttsReady = false;
    String lastKey = "";
    long lastTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(new Locale("en", "IN"));
            ttsReady = true;
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName().equals(getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle e = n.extras;
        StringBuilder sb = new StringBuilder();
        CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = e.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence big = e.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (t != null) sb.append(t).append(' ');
        if (x != null) sb.append(x).append(' ');
        if (big != null) sb.append(big);
        String text = sb.toString();
        String low = text.toLowerCase(Locale.ROOT);

        boolean got = false;
        for (String k : RECEIVED) if (low.contains(k)) got = true;
        if (!got) return;
        for (String k : IGNORE) if (low.contains(k)) return;

        Matcher m = AMOUNT.matcher(text);
        if (!m.find()) return;
        String amt = m.group(1).replace(",", "");

        long now = System.currentTimeMillis();
        String key = sbn.getPackageName() + amt;
        if (key.equals(lastKey) && now - lastTime < 20000) return;
        lastKey = key;
        lastTime = now;

        Intent i = new Intent(MainActivity.ACTION);
        i.setPackage(getPackageName());
        i.putExtra("amount", amt);
        sendBroadcast(i);

        if (ttsReady) {
            tts.speak("Payment received. " + amt + " rupees.", TextToSpeech.QUEUE_FLUSH, null, "pr");
        }
    }

    @Override
    public void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }
}
