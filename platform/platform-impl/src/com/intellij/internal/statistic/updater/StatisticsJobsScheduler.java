// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.updater;

import com.intellij.application.Topics;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.FrameStateListener;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.internal.statistic.service.fus.collectors.FUStatisticsPersistence;
import com.intellij.internal.statistic.service.fus.collectors.LegacyApplicationUsageTriggers;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.util.Disposer;
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

public class StatisticsJobsScheduler implements BaseComponent {
  private static final int SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS = 10 * 60 * 1000;
  private static final int SEND_EVENT_LOG_DELAY_IN_MILLIS = 2 * 60 * 60 * 1000;
  private static final int SEND_STATISTICS_DELAY_IN_MIN = 10;

  public static final int LOG_APPLICATION_STATES_DELAY_IN_MIN = 15;
  public static final int LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN = 30;
  public static final int LOG_PROJECTS_STATES_DELAY_IN_MIN = 12 * 60;

  private static final Map<Project, Future> myPersistStatisticsSessionsMap = Collections.synchronizedMap(new HashMap<>());

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    if (StatisticsUploadAssistant.isShouldShowNotification()) {
      Disposable disposable = Disposer.newDisposable();
      Topics.subscribe(FrameStateListener.TOPIC, disposable, new FrameStateListener() {
        @Override
        public void onFrameActivated() {
          if (isEmpty(((WindowManagerEx)WindowManager.getInstance()).getMostRecentFocusedWindow())) {
            final StatisticsService statisticsService = StatisticsUploadAssistant.getEventLogStatisticsService();
            ApplicationManager.getApplication().invokeLater(() -> StatisticsNotificationManager.showNotification(statisticsService));
            Disposer.dispose(disposable);
          }
        }
      });
    }

    runEventLogStatisticsService();
    runStatesLogging();
    runLegacyDataCleanupService();
  }

  private static void runEventLogStatisticsService() {
    JobScheduler.getScheduler().scheduleWithFixedDelay(() -> {
      if (FeatureUsageLogger.INSTANCE.isEnabled()) {
        runStatisticsServiceWithDelay(StatisticsUploadAssistant.getEventLogStatisticsService(), SEND_STATISTICS_DELAY_IN_MIN);
      }
    }, 2 * SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS, SEND_EVENT_LOG_DELAY_IN_MILLIS, TimeUnit.MILLISECONDS);
  }

  private static void runStatesLogging() {
    if (!StatisticsUploadAssistant.isSendAllowed()) return;
    JobScheduler.getScheduler().schedule(() -> FUStateUsagesLogger.create().logApplicationStates(), LOG_APPLICATION_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        ScheduledFuture<?> future =
          JobScheduler.getScheduler().scheduleWithFixedDelay(() -> FUStateUsagesLogger.create().logProjectStates(project),
                                                             LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN,
                                                             LOG_PROJECTS_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);
        myPersistStatisticsSessionsMap.put(project, future);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        Future future = myPersistStatisticsSessionsMap.remove(project);
        if (future != null) {
          future.cancel(true);
        }
      }
    });
  }


  private static void runLegacyDataCleanupService() {
    JobScheduler.getScheduler().schedule(() -> {
      FUStatisticsPersistence.clearLegacyStates();
      LegacyApplicationUsageTriggers.cleanup();
    }, 1, TimeUnit.MINUTES);
  }

  private static void runStatisticsServiceWithDelay(@NotNull final StatisticsService statisticsService, int delayInMin) {
    JobScheduler.getScheduler().schedule(statisticsService::send, delayInMin, TimeUnit.MINUTES);
  }

  public StatisticsJobsScheduler() {
    NotificationsConfigurationImpl.remove("SendUsagesStatistics");
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
}
