package com.intellij.internal.statistic.updater;

import com.intellij.internal.statistic.StatisticsBundle;
import com.intellij.internal.statistic.configurable.StatisticsConfigurable;
import com.intellij.internal.statistic.connect.RemotelyConfigurableStatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

public class StatisticsNotificationManager {

  public static final String GROUP_DISPLAY_ID = "IDE Usage Statistics";

  private StatisticsNotificationManager() {
  }

  public static void showNotification(@NotNull RemotelyConfigurableStatisticsService statisticsService, Project project) {

    Notifications.Bus.notify(new Notification(GROUP_DISPLAY_ID, getTitle(),
                                              getText(),
                                              NotificationType.INFORMATION,
                                              new MyNotificationListener(statisticsService, UsageStatisticsPersistenceComponent
                                                .getInstance())), project);
  }

  private static String getText() {
    return
      "<html>Please click <a href='allow'>I agree</a> if you want to help make "+ ApplicationNamesInfo.getInstance().getFullProductName() +
      " better or <a href='decline'>I don't agree</a> otherwise. <a href='settings'>more...</a></html>";
  }

  private static String getTitle() {
    String product = ApplicationNamesInfo.getInstance().getFullProductName();
    String company = ApplicationInfo.getInstance().getCompanyName();
    return StatisticsBundle.message("stats.title", product, company);
  }

  private static class MyNotificationListener implements NotificationListener {
    private RemotelyConfigurableStatisticsService myStatisticsService;
    private final UsageStatisticsPersistenceComponent mySettings;

    public MyNotificationListener(@NotNull RemotelyConfigurableStatisticsService statisticsService,
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
