// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.connection.EventLogSendListener;
import com.intellij.internal.statistic.eventLog.connection.EventLogStatisticsService;
import com.intellij.internal.statistic.eventLog.connection.EventLogUploadSettingsService;
import com.intellij.internal.statistic.eventLog.connection.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StatisticsUploadAssistant {
  private static final String IDEA_HEADLESS_ENABLE_STATISTICS = "idea.headless.enable.statistics";
  private static final String IDEA_SUPPRESS_REPORT_STATISTICS = "idea.suppress.statistics.report";
  private static final String ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT = "idea.local.statistics.without.report";
  private static final String USE_TEST_STATISTICS_SEND_ENDPOINT = "idea.use.test.statistics.send.endpoint";
  private static final String USE_TEST_STATISTICS_CONFIG = "idea.use.test.statistics.config";
  private static final String DISABLE_COLLECT_STATISTICS = "idea.disable.collect.statistics";

  private StatisticsUploadAssistant() {}

  public static boolean isSendAllowed() {
    if (isSuppressStatisticsReport() || isLocalStatisticsWithoutReport()) {
      return false;
    }

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return isHeadlessStatisticsEnabled();
    }

    UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();
    return settings != null && settings.isAllowed();
  }

  public static boolean isCollectAllowed() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return isHeadlessStatisticsEnabled();
    }

    if (!isDisableCollectStatistics() && !isCollectionForceDisabled()) {
      UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();
      if ((settings != null && settings.isAllowed()) || isLocalStatisticsWithoutReport()) {
        return true;
      }
    }
    return false;
  }

  private static boolean isForceCollectEnabled() {
    return StatisticsEventLogProviderUtil.forceLoggingAlwaysEnabled();
  }

  public static boolean isCollectAllowedOrForced() {
    return isCollectAllowed() || isForceCollectEnabled();
  }

  public static boolean isCollectionForceDisabled() {
    ExternalEventLogSettings externalEventLogSettings = StatisticsEventLogProviderUtil.getExternalEventLogSettings();
    return externalEventLogSettings != null && externalEventLogSettings.forceDisableCollectionConsent();
  }

  @NlsContexts.DetailedDescription
  public static @Nullable String getConsentWarning() {
    ExternalEventLogSettings externalEventLogSettings = StatisticsEventLogProviderUtil.getExternalEventLogSettings();
    return externalEventLogSettings == null ? null : externalEventLogSettings.getConsentWarning();
  }

  private static boolean isHeadlessStatisticsEnabled() {
    return Boolean.getBoolean(IDEA_HEADLESS_ENABLE_STATISTICS);
  }

  public static boolean isTestStatisticsEnabled() {
    return isLocalStatisticsWithoutReport()
           || isTeamcityDetected()
           || isUseTestStatisticsSendEndpoint()
           || isUseTestStatisticsConfig();
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

    return new EventLogStatisticsService(
      EventLogInternalSendConfig.createByRecorder(recorderId, true),
      new EventLogInternalApplicationInfo(isUseTestStatisticsConfig(), isUseTestStatisticsSendEndpoint()),
      listener
    );
  }

  public static EventLogUploadSettingsService createExternalSettings(@NotNull String recorderId, boolean isTestConfig, boolean isTestSendEndpoint, long cacheTimeoutMs) {
    return new EventLogUploadSettingsService(recorderId, new EventLogInternalApplicationInfo(isTestConfig, isTestSendEndpoint), cacheTimeoutMs);
  }

  public static boolean isTeamcityDetected() {
    return Strings.isNotEmpty(System.getenv("TEAMCITY_VERSION"));
  }

  public static boolean isSuppressStatisticsReport() {
    return Boolean.getBoolean(IDEA_SUPPRESS_REPORT_STATISTICS);
  }

  public static boolean isLocalStatisticsWithoutReport() {
    return Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT);
  }

  public static boolean isUseTestStatisticsSendEndpoint() {
    return Boolean.getBoolean(USE_TEST_STATISTICS_SEND_ENDPOINT);
  }

  public static boolean isUseTestStatisticsConfig() {
    return Boolean.getBoolean(USE_TEST_STATISTICS_CONFIG);
  }

  public static boolean isDisableCollectStatistics() {
    return Boolean.getBoolean(DISABLE_COLLECT_STATISTICS);
  }
}
