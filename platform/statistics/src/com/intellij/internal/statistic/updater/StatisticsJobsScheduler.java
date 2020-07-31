// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.updater;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.StatisticsNotificationManager;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.eventLog.StatisticsEventLogMigration;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader;
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class StatisticsJobsScheduler implements ApplicationInitializedListener {
  private static final int SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS = 5 * 60 * 1000;
  private static final int CHECK_STATISTICS_PROVIDERS_DELAY_IN_MIN = 1;
  private static final int CHECK_EXTERNAL_UPLOADER_DELAY_IN_MIN = 3;

  public static final int LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN = 15;
  public static final int LOG_APPLICATION_STATES_DELAY_IN_MIN = 24 * 60;
  public static final int LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN = 30;
  public static final int LOG_PROJECTS_STATES_DELAY_IN_MIN = 12 * 60;

  private static final Map<Project, Future<?>> myPersistStatisticsSessionsMap = Collections.synchronizedMap(new HashMap<>());

  StatisticsJobsScheduler() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Override
  public void componentsInitialized() {
    final StatisticsNotificationManager notificationManager = ServiceManager.getService(StatisticsNotificationManager.class);
    if (notificationManager != null) {
      notificationManager.showNotificationIfNeeded();
    }

    checkPreviousExternalUploadResult();
    runEventLogStatisticsService();
    runStatesLogging();
    runWhitelistStorageUpdater();

    StatisticsEventLogMigration.performMigration();
  }

  private static void runWhitelistStorageUpdater() {
    JobScheduler.getScheduler().scheduleWithFixedDelay(
      () -> {
        final List<StatisticsEventLoggerProvider> providers = StatisticsEventLoggerKt.getEventLogProviders();
        for (StatisticsEventLoggerProvider provider : providers) {
          if (provider.isRecordEnabled()) {
            SensitiveDataValidator.getInstance(provider.getRecorderId()).update();
          }
        }
      }, 3, 180, TimeUnit.MINUTES);
  }

  private static void checkPreviousExternalUploadResult() {
    JobScheduler.getScheduler().schedule(() -> {
      StatisticsEventLoggerProvider config = FeatureUsageLogger.INSTANCE.getConfig();
      if (config.isRecordEnabled()) {
        EventLogExternalUploader.INSTANCE.logPreviousExternalUploadResult(config.getRecorderId());
      }
    }, CHECK_EXTERNAL_UPLOADER_DELAY_IN_MIN, TimeUnit.MINUTES);
  }


  private static void runEventLogStatisticsService() {
    JobScheduler.getScheduler().schedule(() -> {
      final List<StatisticsEventLoggerProvider> providers = StatisticsEventLoggerKt.getEventLogProviders();
      for (StatisticsEventLoggerProvider provider : providers) {
        if (provider.isSendEnabled()) {
          final StatisticsService statisticsService = StatisticsUploadAssistant.getEventLogStatisticsService(provider.getRecorderId());
          runStatisticsServiceWithDelay(statisticsService, provider.getSendFrequencyMs());
        }
      }
    }, CHECK_STATISTICS_PROVIDERS_DELAY_IN_MIN, TimeUnit.MINUTES);
  }

  private static void runStatesLogging() {
    if (!StatisticsUploadAssistant.isSendAllowed()) return;
    JobScheduler.getScheduler().scheduleWithFixedDelay(() -> FUStateUsagesLogger.create().logApplicationStates(),
                                                       LOG_APPLICATION_STATES_INITIAL_DELAY_IN_MIN,
                                                       LOG_APPLICATION_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);

    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        ScheduledFuture<?> future = JobScheduler.getScheduler().scheduleWithFixedDelay(
          () -> FUStateUsagesLogger.create().logProjectStates(project, new EmptyProgressIndicator()),
          LOG_PROJECTS_STATES_INITIAL_DELAY_IN_MIN,
          LOG_PROJECTS_STATES_DELAY_IN_MIN, TimeUnit.MINUTES);
        myPersistStatisticsSessionsMap.put(project, future);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        Future<?> future = myPersistStatisticsSessionsMap.remove(project);
        if (future != null) {
          future.cancel(true);
        }
      }
    });
  }

  private static void runStatisticsServiceWithDelay(@NotNull final StatisticsService statisticsService, long delayInMs) {
    JobScheduler.getScheduler().scheduleWithFixedDelay(
      statisticsService::send, SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS, delayInMs, TimeUnit.MILLISECONDS
    );
  }
}
