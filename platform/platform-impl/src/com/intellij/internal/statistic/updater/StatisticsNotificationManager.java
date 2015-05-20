package com.intellij.internal.statistic.updater;

import com.intellij.internal.statistic.configurable.StatisticsConfigurable;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

public class StatisticsNotificationManager {

  public static final String GROUP_DISPLAY_ID = "IDE Usage Statistics";

  private StatisticsNotificationManager() {
  }

  public static void showNotification(@NotNull StatisticsService statisticsService) {
    MyNotificationListener listener =
      new MyNotificationListener(statisticsService, UsageStatisticsPersistenceComponent.getInstance());

    Notifications.Bus.notify(statisticsService.createNotification(GROUP_DISPLAY_ID, listener));
  }

  private static class MyNotificationListener implements NotificationListener {
    private StatisticsService myStatisticsService;
    private final UsageStatisticsPersistenceComponent mySettings;

    public MyNotificationListener(@NotNull StatisticsService statisticsService,
                                  @NotNull UsageStatisticsPersistenceComponent settings) {
      myStatisticsService = statisticsService;
      mySettings = settings;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        final String description = event.getDescription();
        if ("allow".equals(description)) {
          mySettings.setAllowed(true);
          mySettings.setShowNotification(false);
          notification.expire();

          ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
            @Override
            public void run() {
               myStatisticsService.send();
            }
          });
        }
        else if ("decline".equals(description)) {
          mySettings.setAllowed(false);
          mySettings.setShowNotification(false);
          notification.expire();
        }
        else if ("settings".equals(description)) {
          final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
          IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
          util.editConfigurable((JFrame)ideFrame, new StatisticsConfigurable(true));
          notification.expire();
        }
      }
    }
  }
}
