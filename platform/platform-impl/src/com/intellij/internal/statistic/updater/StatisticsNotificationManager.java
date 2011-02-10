package com.intellij.internal.statistic.updater;

import com.intellij.internal.statistic.connect.RemotelyConfigurableStatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.configurable.StatisticsConfigurable;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

public class StatisticsNotificationManager {
  private StatisticsNotificationManager() {
  }

  public static void showNotification(@NotNull RemotelyConfigurableStatisticsService statisticsService) {
    Notifications.Bus.notify(new Notification("SendUsagesStatistics", getTitle(),
                                              getText(),
                                              NotificationType.INFORMATION,
                                              new MyNotificationListener(statisticsService, UsageStatisticsPersistenceComponent
                                                .getInstance())));
  }

  private static String getText() {
    return
      "<html>We're asking your permission to send information about your IntelliJ IDEA plugins configuration (what is enabled and what is not), and feature usage statistics (e.g. how frequently you're using code completion)." +
      "<br/>" +
      "This data is anonymous, does not contain any personal information, collected for use only by JetBrains, and will never be transmitted to any third party." +
      "<br/>" +
      "Please click <a href='allow'>Agree</a> if you want to help make IntelliJ IDEA better  " +
      "or <a href='decline'>Don't agree</a> otherwise." +
      "<br/>" +
      "You can customize settings on <a href='ettings'>IDE Settings -> Usage statistics</a> pane" +
      "</html>";
  }

  private static String getTitle() {
    return "Help improve IntelliJ IDEA by sending anonymous usage statistics to JetBrains";
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

          myStatisticsService.send();
        }
        else if ("decline".equals(description)) {
          mySettings.setAllowed(false);
          mySettings.setShowNotification(false);
          notification.expire();
        }
        else if ("settings".equals(description)) {
          final ShowSettingsUtil util = ShowSettingsUtil.getInstance();
          IdeFrame ideFrame = WindowManagerEx.getInstanceEx().findFrameFor(null);
          util.editConfigurable((JFrame)ideFrame, new StatisticsConfigurable());
          notification.expire();
        }
      }
    }
  }
}
