// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification;

import com.intellij.ide.IdeBundle;
import com.intellij.notification.impl.NotificationsToolWindowFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ActionCenter {
  public static @NotNull List<Notification> getNotifications(@NotNull Project project) {
    if (isEnabled()) {
      return NotificationsToolWindowFactory.Companion.getNotifications(project);
    }
    return EventLog.getNotifications(project);
  }

  public static void showNotification(@NotNull Project project, @NotNull String groupId, @NotNull List<String> ids) {
    if (isEnabled()) {
      // XXX
    }
    else {
      EventLog.showNotification(project, groupId, ids);
    }
  }

  public static void expireNotifications(@Nullable Project project) {
    List<Notification> notifications =
      isEnabled() ? NotificationsToolWindowFactory.Companion.getNotifications(project) : EventLog.getLogModel(project).getNotifications();

    for (Notification notification : notifications) {
      notification.expire();
    }
  }

  public static @Nullable ToolWindow getToolwindow(@Nullable Project project) {
    if (isEnabled()) {
      return project == null ? null : ToolWindowManager.getInstance(project).getToolWindow(NotificationsToolWindowFactory.ID);
    }
    return EventLog.getEventLog(project);
  }

  public static @NotNull @Nls String getToolwindowName() {
    return IdeBundle.message(isEnabled() ? "toolwindow.stripe.Notifications" : "toolwindow.stripe.Event_Log");
  }

  public static boolean isEnabled() {
    return Registry.is("ide.notification.action.center", false);
  }
}