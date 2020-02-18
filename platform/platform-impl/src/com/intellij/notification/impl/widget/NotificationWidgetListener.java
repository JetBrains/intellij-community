// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.widget;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class NotificationWidgetListener implements UISettingsListener, ToolWindowManagerListener, Runnable {
  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    updateWidgetAndIcon();
  }

  @Override
  public void toolWindowRegistered(@NotNull String id) {
    if (EventLog.LOG_TOOL_WINDOW_ID.equals(id)) {
      updateWidgetAndIcon();
    }
  }

  @Override
  public void run() {
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
    NotificationWidgetFactory widgetFactory = StatusBarWidgetFactory.EP_NAME.findExtension(NotificationWidgetFactory.class);

    for (Project project : projectManager.getOpenProjects()) {
      if (widgetIsAvailable) {
        if (widgetFactory != null) {
          StatusBarWidgetsManager widgetsManager = project.getService(StatusBarWidgetsManager.class);
          if (!widgetsManager.wasWidgetCreated(widgetFactory)) {
            widgetsManager.updateWidget(widgetFactory);
          }
        }
      }
      else {
        updateToolWindowNotificationsIcon(project);
        if (widgetFactory != null) {
          project.getService(StatusBarWidgetsManager.class).updateWidget(widgetFactory);
        }
      }
    }

  }

  private static void updateToolWindowNotificationsIcon(@NotNull Project project) {
    ToolWindow eventLog = EventLog.getEventLog(project);
    if (eventLog != null) {
      ArrayList<Notification> notifications = EventLog.getLogModel(project).getNotifications();
      eventLog.setIcon(IdeNotificationArea.createIconWithNotificationCount(new JBLabel(),
                                                                           IdeNotificationArea.getMaximumType(notifications),
                                                                           notifications.size(), true));
    }
  }
}
