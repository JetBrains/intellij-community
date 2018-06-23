// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.updater;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.FrameStateListener;
import com.intellij.ide.FrameStateManager;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence.persistProjectUsages;

public class StatisticsJobsScheduler implements ApplicationComponent {
  private static final int SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS = 10 * 60 * 1000;
  private static final int SEND_STATISTICS_DELAY_IN_MIN = 10;

  public static final int PERSIST_SESSIONS_INITIAL_DELAY_IN_MIN = 30;
  public static final int PERSIST_SESSIONS_DELAY_IN_MIN = 12 * 60;

  private final FrameStateManager myFrameStateManager;

  private static final Map<Project, Future> myPersistStatisticsSessionsMap = Collections.synchronizedMap(new HashMap<>());

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    runStatisticsService();
    runStatisticsSessionsPersistence();
  }

  public StatisticsJobsScheduler(@NotNull FrameStateManager frameStateManager) {
    NotificationsConfigurationImpl.remove("SendUsagesStatistics");
    myFrameStateManager = frameStateManager;
  }

  private static boolean isEmpty(Window window) {
    if (window instanceof IdeFrameImpl) {
      BalloonLayout layout = ((IdeFrameImpl)window).getBalloonLayout();
      if (layout instanceof BalloonLayoutImpl) {
        // do not show notification if others exist
        return ((BalloonLayoutImpl)layout).isEmpty();
      }
    }
    return false;
  }

  private void runStatisticsService() {
    if (StatisticsUploadAssistant.isShouldShowNotification()) {
      myFrameStateManager.addListener(new FrameStateListener() {
        @Override
        public void onFrameActivated() {
          if (isEmpty(((WindowManagerEx)WindowManager.getInstance()).getMostRecentFocusedWindow())) {
            final StatisticsService statisticsService = StatisticsUploadAssistant.getApprovedGroupsStatisticsService();
            ApplicationManager.getApplication().invokeLater(() -> StatisticsNotificationManager.showNotification(statisticsService));
            myFrameStateManager.removeListener(this);
          }
        }
      });
    }

    JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
      final StatisticsService statisticsService = StatisticsUploadAssistant.getApprovedGroupsStatisticsService();
      if (StatisticsUploadAssistant.isSendAllowed() && StatisticsUploadAssistant.isTimeToSend()) {
        runStatisticsServiceWithDelay(statisticsService, SEND_STATISTICS_DELAY_IN_MIN);
      }
      if (FeatureUsageLogger.INSTANCE.isEnabled() && StatisticsUploadAssistant.isTimeToSendEventLog()) {
        runStatisticsServiceWithDelay(StatisticsUploadAssistant.getEventLogStatisticsService(), 3 * SEND_STATISTICS_DELAY_IN_MIN);
      }
    }, SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS, StatisticsUploadAssistant.getSendPeriodInMillis(), TimeUnit.MILLISECONDS);
  }

  private static void runStatisticsServiceWithDelay(@NotNull final StatisticsService statisticsService, int delayInMin) {
    JobScheduler.getScheduler().schedule(statisticsService::send, delayInMin, TimeUnit.MINUTES);
  }

  private static void runStatisticsSessionsPersistence() {
    if (!StatisticsUploadAssistant.isSendAllowed()) return;

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        ScheduledFuture<?> future =
          JobScheduler.getScheduler().scheduleWithFixedDelay(() -> persistProjectUsages(project), PERSIST_SESSIONS_INITIAL_DELAY_IN_MIN,
                                                             PERSIST_SESSIONS_DELAY_IN_MIN, TimeUnit.MINUTES);
        myPersistStatisticsSessionsMap.put(project, future);
      }

      public void projectClosed(Project project) {
        Future future = myPersistStatisticsSessionsMap.remove(project);
        if (future != null) {
          future.cancel(true);
        }
      }
    });
  }
}
