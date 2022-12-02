// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.widget;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class NotificationWidgetListener implements UISettingsListener, ToolWindowManagerListener, EventLogListener {
  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updateWidgetAndIcon();
  }

  @Override
  public void toolWindowsRegistered(@NotNull List<String> ids, @NotNull ToolWindowManager toolWindowManager) {
    for (String id : ids) {
      if (EventLog.LOG_TOOL_WINDOW_ID.equals(id)) {
        updateWidgetAndIcon();
      }
    }
  }

  @Override
  public void modelChanged() {
    if (NotificationWidgetFactory.isAvailable()) {
      return;
    }
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager == null) {
      return;
    }
    for (Project project : projectManager.getOpenProjects()) {
      if (!NotificationWidgetFactory.isAvailable()) {
        updateToolWindowNotificationsIcon(project);
      }
    }
  }

  private static void updateWidgetAndIcon() {
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    if (projectManager == null) {
      return;
    }

    boolean widgetIsAvailable = NotificationWidgetFactory.isAvailable();
    for (Project project : projectManager.getOpenProjects()) {
      if (!widgetIsAvailable) {
        updateToolWindowNotificationsIcon(project);
      }
      project.getService(StatusBarWidgetsManager.class).updateWidget(NotificationWidgetFactory.class);
    }
  }

  private static void updateToolWindowNotificationsIcon(@NotNull Project project) {
    if (ActionCenter.isEnabled()) {
      return;
    }

    ToolWindow eventLog = EventLog.getEventLog(project);
    if (eventLog != null) {
      List<Notification> notifications = EventLog.getNotifications(project);
      NotificationType type = NotificationType.getDominatingType(notifications);
      int size = notifications.size();
      ApplicationManager.getApplication().invokeLater(() -> {
        eventLog.setIcon(IdeNotificationArea.createIconWithNotificationCount(new JBLabel(), type, size, true));
      });
    }
  }
}
