/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification.impl;

import com.intellij.ide.DataManager;
import com.intellij.notification.EventLog;
import com.intellij.notification.LogModel;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author spleaner
 */
public class IdeNotificationArea implements StatusBarWidget, StatusBarWidget.IconPresentation {
  private static final Icon EMPTY_ICON = IconLoader.getIcon("/ide/notifications.png");
  private static final Icon ERROR_ICON = IconLoader.getIcon("/ide/error_notifications.png");
  private static final Icon WARNING_ICON = IconLoader.getIcon("/ide/warning_notifications.png");
  private static final Icon INFO_ICON = IconLoader.getIcon("/ide/info_notifications.png");

  private Icon myCurrentIcon = EMPTY_ICON;
  private StatusBar myStatusBar;
  private final Alarm myLogAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public IdeNotificationArea() {
    Disposer.register(this, myLogAlarm);

  }

  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return this;
  }

  public void dispose() {
  }

  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;

    new Runnable() {
      @Override
      public void run() {
        LogModel logModel = EventLog.getLogModel(getProject());
        ToolWindow eventLog = EventLog.getEventLog(getProject());
        if (eventLog != null && eventLog.isVisible()) {
          logModel.logShown();
        }
        updateStatus();
        myLogAlarm.addRequest(this, 50);
      }
    }.run();

  }

  @Nullable
  private Project getProject() {
    return PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext((Component) myStatusBar));
  }

  public Consumer<MouseEvent> getClickConsumer() {
    return new Consumer<MouseEvent>() {
      public void consume(MouseEvent mouseEvent) {
        EventLog.toggleLog(getProject());
      }
    };
  }

  @NotNull
  public Icon getIcon() {
    return myCurrentIcon;
  }

  public String getTooltipText() {
    final NotificationsManagerImpl manager = NotificationsManagerImpl.getNotificationsManagerImpl();
    if (manager.hasNotifications(getProject())) {
      final int count = manager.count(getProject());
      return String.format("%s notification%s pending", count, count == 1 ? "" : "s");
    }

    return "No new notifications";
  }

  @NotNull
  public String ID() {
    return "Notifications";
  }

  private void updateStatus() {
    myCurrentIcon = getPendingNotificationsIcon(EMPTY_ICON, NotificationModel.getMaximumType(EventLog.getLogModel(getProject()).getNotifications()));
    myStatusBar.updateWidget(ID());
  }

  private static Icon getPendingNotificationsIcon(Icon defIcon, final NotificationType maximumType) {
    if (maximumType != null) {
      switch (maximumType) {
        case WARNING: return WARNING_ICON;
        case ERROR: return ERROR_ICON;
        case INFORMATION: return INFO_ICON;
      }
    }
    return defIcon;
  }

}
