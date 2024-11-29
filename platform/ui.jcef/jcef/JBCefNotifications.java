// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.RegistryManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

final class JBCefNotifications {
  static @Nullable Component createStubPanel(JBCefHealthMonitor.Status status) {
    return switch (status) {
      case UNKNOWN, OK -> null;
      case UNPRIVILEGED_USER_NS_DISABLED -> createUnprivilegedUserNSStubPanel();
      case RUN_UNDER_SUPER_USER -> createTextComponent(
        IdeBundle.message("notification.content.jcef.super.user.error.message"));
      case GPU_PROCESS_FAILED -> createTextComponent(IdeBundle.message("notification.content.jcef.gpu.process.failed.error.message"));
    };
  }

  static void showAppArmorNotification() {
    Notification notification =
      JBCefApp.getNotificationGroup()
        .createNotification(
          IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.title"),
          IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.message"),
          NotificationType.WARNING);

    AnAction installProfileAction = JBCefAppArmorUtils.getInstallInstallAppArmorProfileAction(() -> notification.expire());
    if (installProfileAction != null) {
      notification.addAction(installProfileAction);
    }

    notification.addAction(
      NotificationAction.createSimple(
        IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.disable.sandbox"),
        () -> {
          RegistryManager.getInstance().get("ide.browser.jcef.sandbox.enable").setValue(false);
          notification.expire();
          ApplicationManager.getApplication().restart();
        })
    );

    notification.addAction(
      NotificationAction.createSimple(
        IdeBundle.message("notification.content.jcef.unprivileged.userns.restricted.action.learn.more"),
        () -> {
          // TODO(kharitonov): move to https://intellij-support.jetbrains.com/hc/en-us/sections/201620045-Troubleshooting
          BrowserUtil.browse("https://youtrack.jetbrains.com/articles/JBR-A-11");
        })
    );

    Notifications.Bus.notify(notification);
  }

  private static JComponent createUnprivilegedUserNSStubPanel() {
    return JBCefAppArmorUtils.getUnprivilegedUserNamespacesRestrictedStubPanel();
  }

  private static JComponent createTextComponent(@Nls String text) {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.anchor = GridBagConstraints.CENTER;
    c.fill = GridBagConstraints.NONE;
    panel.add(new JLabel(text), c);
    return panel;
  }
}
