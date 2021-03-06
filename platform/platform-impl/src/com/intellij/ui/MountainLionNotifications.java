// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.jna.JnaLoader;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ui.mac.foundation.Foundation.invoke;
import static com.intellij.ui.mac.foundation.Foundation.nsString;

/**
 * @author Dennis.Ushakov
 */
final class MountainLionNotifications implements SystemNotificationsImpl.Notifier {
  private static MountainLionNotifications ourInstance;

  public static synchronized MountainLionNotifications getInstance() {
    if (ourInstance == null && JnaLoader.isLoaded()) {
      ourInstance = new MountainLionNotifications();
    }
    return ourInstance;
  }

  private MountainLionNotifications() {
    final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {
      @Override
      public void applicationActivated(@NotNull IdeFrame ideFrame) {
        cleanupDeliveredNotifications();
      }
    });
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        cleanupDeliveredNotifications();
      }
    });
  }

  @Override
  public void notify(@NotNull String name, @NotNull String title, @NotNull String description) {
    final ID notification = invoke(Foundation.getObjcClass("NSUserNotification"), "new");
    invoke(notification, "setTitle:", nsString(StringUtil.stripHtml(title, true).replace("%", "%%")));
    invoke(notification, "setInformativeText:", nsString(StringUtil.stripHtml(description, true).replace("%", "%%")));
    final ID center = invoke(Foundation.getObjcClass("NSUserNotificationCenter"), "defaultUserNotificationCenter");
    invoke(center, "deliverNotification:", notification);
  }

  private static void cleanupDeliveredNotifications() {
    final ID center = invoke(Foundation.getObjcClass("NSUserNotificationCenter"), "defaultUserNotificationCenter");
    invoke(center, "removeAllDeliveredNotifications");
  }
}