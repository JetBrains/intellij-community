// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.connection.EventLogSendListener;
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService;
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsService;
import com.intellij.internal.statistic.eventLog.connection.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class StatisticsUploadAssistant {
  private static final String IDEA_HEADLESS_ENABLE_STATISTICS = "idea.headless.enable.statistics";
  private static final String IDEA_SUPPRESS_REPORT_STATISTICS = "idea.suppress.statistics.report";
  private static final String ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT = "idea.local.statistics.without.report";

  public static final Object LOCK = new Object();

  private StatisticsUploadAssistant() {}

  public static boolean isSendAllowed() {
    if (isSuppressStatisticsReport() || isLocalStatisticsWithoutReport()) {
      return false;
    }

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return isHeadlessStatisticsEnabled();
    }
    UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();

    boolean sendOverride = getSendAllowedOverride();
    return settings != null && settings.isAllowed() || sendOverride;
  }

  public static boolean isCollectAllowed() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return isHeadlessStatisticsEnabled();
    }
    final UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();
    boolean collectOverride = getCollectAllowedOverride();
    return (settings != null && settings.isAllowed() || collectOverride) || isLocalStatisticsWithoutReport();
  }

  public static boolean getSendAllowedOverride() {
    ExternalEventLogSettings externalEventLogSettings = StatisticsEventLogProviderUtil.getExternalEventLogSettings();
    if (externalEventLogSettings != null)
      return externalEventLogSettings.isSendAllowedOverride();
    else
      return false;
  }

  public static boolean getCollectAllowedOverride() {
    ExternalEventLogSettings externalEventLogSettings = StatisticsEventLogProviderUtil.getExternalEventLogSettings();
    if (externalEventLogSettings != null)
      return externalEventLogSettings.isCollectAllowedOverride();
    else
      return false;
  }

  private static boolean isHeadlessStatisticsEnabled() {
    return Boolean.getBoolean(IDEA_HEADLESS_ENABLE_STATISTICS);
  }

  public static boolean isTestStatisticsEnabled() {
    return isLocalStatisticsWithoutReport() || isTeamcityDetected();
  }

  public static @NotNull StatisticsService getEventLogStatisticsService(@NotNull String recorderId) {
    EventLogSendListener listener = new EventLogSendListener() {
      @Override
      public void onLogsSend(@NotNull List<String> successfullySentFiles,
                             @NotNull List<Integer> errors,
                             int totalLocalFiles) {
        int success = successfullySentFiles.size();
        int failed = errors.size();
        EventLogSystemLogger.logFilesSend(
          recorderId, totalLocalFiles, success, failed, false, successfullySentFiles, errors
        );
      }
    };

    EventLogRecorderConfiguration configuration = EventLogConfiguration.getInstance().getOrCreate(recorderId);
    return new EventLogStatisticsService(
      new DeviceConfiguration(configuration.getDeviceId(), configuration.getBucket(), configuration.getMachineId()),
      new EventLogInternalRecorderConfig(recorderId),
      new EventLogInternalApplicationInfo(recorderId, false), listener
    );
  }

  public static EventLogUploadSettingsService createExternalSettings(@NotNull String recorderId, boolean isTest, long cacheTimeoutMs) {
    return new EventLogUploadSettingsService(recorderId, new EventLogInternalApplicationInfo(recorderId, isTest), cacheTimeoutMs);
  }

  public static boolean isTeamcityDetected() {
    return StringUtil.isNotEmpty(System.getenv("TEAMCITY_VERSION"));
  }

  public static boolean isSuppressStatisticsReport() {
    return Boolean.getBoolean(IDEA_SUPPRESS_REPORT_STATISTICS);
  }

  public static boolean isLocalStatisticsWithoutReport() {
    return Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT);
  }
}
