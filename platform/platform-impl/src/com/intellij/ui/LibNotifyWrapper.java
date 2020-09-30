// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.util.messages.MessageBusConnection;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Fokin
 */
final class LibNotifyWrapper implements SystemNotificationsImpl.Notifier {
  private static LibNotifyWrapper ourInstance;

  public static synchronized LibNotifyWrapper getInstance() {
    if (ourInstance == null && JnaLoader.isLoaded()) {
      ourInstance = new LibNotifyWrapper();
    }
    return ourInstance;
  }

  @SuppressWarnings({"SpellCheckingInspection", "UnusedReturnValue"})
  private interface LibNotify extends Library {
    int notify_init(String appName);
    void notify_uninit();
    Pointer notify_notification_new(String summary, String body, String icon);
    int notify_notification_show(Pointer notification, Pointer error);
  }

  private final LibNotify myLibNotify;
  private final String myIcon;
  private final Object myLock = new Object();
  private boolean myDisposed = false;

  private LibNotifyWrapper() {
    myLibNotify = Native.load("libnotify.so.4", LibNotify.class);

    String appName = ApplicationNamesInfo.getInstance().getProductName();
    if (myLibNotify.notify_init(appName) == 0) {
      throw new IllegalStateException("notify_init failed");
    }

    String icon = AppUIUtil.findIcon();
    myIcon = icon != null ? icon : "dialog-information";

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        synchronized (myLock) {
          myDisposed = true;
          myLibNotify.notify_uninit();
        }
      }
    });
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      synchronized (myLock) {
        if (!myDisposed) {
          Pointer notification = myLibNotify.notify_notification_new(title, description, myIcon);
          myLibNotify.notify_notification_show(notification, null);
        }
      }
    });
  }
}