// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.updater;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.StatisticsNotificationManager;
import com.intellij.internal.statistic.eventLog.StatisticsEventLogMigration;
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerKt;
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider;
import com.intellij.internal.statistic.eventLog.connection.StatisticsService;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader;
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator;
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.DumbService;
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

@InternalIgnoreDependencyViolation
final class StatisticsJobsScheduler implements ApplicationInitializedListener {
  private static final int SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS = 5 * 60 * 1000;
  private static final int CHECK_STATISTICS_PROVIDERS_DELAY_IN_MIN = 1;
  private static final int CHECK_EXTERNAL_UPLOADER_DELAY_IN_MIN = 3;

  StatisticsJobsScheduler() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @Override
  public void componentsInitialized() {
    StatisticsNotificationManager notificationManager = ApplicationManager.getApplication().getService(StatisticsNotificationManager.class);
    if (notificationManager != null) {
      notificationManager.showNotificationIfNeeded();
    }

    checkPreviousExternalUploadResult();
    runEventLogStatisticsService();
    runValidationRulesUpdate();

    StatisticsEventLogMigration.performMigration();
  }

  private static void runValidationRulesUpdate() {
    JobScheduler.getScheduler().scheduleWithFixedDelay(
      () -> {
        final List<StatisticsEventLoggerProvider> providers = StatisticsEventLogProviderUtil.getEventLogProviders();
        for (StatisticsEventLoggerProvider provider : providers) {
          if (provider.isRecordEnabled()) {
            IntellijSensitiveDataValidator.getInstance(provider.getRecorderId()).update();
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
      final List<StatisticsEventLoggerProvider> providers = StatisticsEventLogProviderUtil.getEventLogProviders();
      for (StatisticsEventLoggerProvider provider : providers) {
        if (provider.isSendEnabled()) {
          final StatisticsService statisticsService = StatisticsUploadAssistant.getEventLogStatisticsService(provider.getRecorderId());
          runStatisticsServiceWithDelay(statisticsService, provider.getSendFrequencyMs());
        }
      }
    }, CHECK_STATISTICS_PROVIDERS_DELAY_IN_MIN, TimeUnit.MINUTES);
  }

  private static void runStatisticsServiceWithDelay(@NotNull final StatisticsService statisticsService, long delayInMs) {
    JobScheduler.getScheduler().scheduleWithFixedDelay(
      statisticsService::send, SEND_STATISTICS_INITIAL_DELAY_IN_MILLIS, delayInMs, TimeUnit.MILLISECONDS
    );
  }
}
