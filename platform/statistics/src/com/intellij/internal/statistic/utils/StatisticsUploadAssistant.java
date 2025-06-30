// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils;

import com.intellij.ide.ConsentOptionsProvider;
import com.intellij.internal.statistic.eventLog.EventLogInternalApplicationInfo;
import com.intellij.internal.statistic.eventLog.EventLogInternalSendConfig;
import com.intellij.internal.statistic.eventLog.ExternalEventLogSettings;
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil;
import com.intellij.internal.statistic.eventLog.connection.*;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

import static com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider;

public final class StatisticsUploadAssistant {
  private static final String IDEA_HEADLESS_ENABLE_STATISTICS = "idea.headless.enable.statistics";
  private static final String IDEA_SUPPRESS_REPORT_STATISTICS = "idea.suppress.statistics.report";
  private static final String ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT = "idea.local.statistics.without.report";
  private static final String USE_TEST_STATISTICS_SEND_ENDPOINT = "idea.use.test.statistics.send.endpoint";
  private static final String USE_TEST_STATISTICS_CONFIG = "idea.use.test.statistics.config";
  private static final String DISABLE_COLLECT_STATISTICS = "idea.disable.collect.statistics";

  private StatisticsUploadAssistant() {}

  public static boolean isSendAllowed() {
    return isSendAllowed(() -> isAllowedByUserConsent());
  }

  public static boolean isSendAllowed(@NotNull BooleanSupplier isAllowedByUserConsent) {
    if (isSuppressStatisticsReport() || isLocalStatisticsWithoutReport()) {
      return false;
    }

    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return isHeadlessStatisticsEnabled();
    }

    return isAllowedByUserConsent.getAsBoolean();
  }

  public static boolean isCollectAllowed() {
    return isCollectAllowed(() -> isAllowedByUserConsent());
  }

  public static boolean isCollectAllowed(@NotNull BooleanSupplier isAllowedByUserConsent) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return isHeadlessStatisticsEnabled();
    }

    if (!isDisableCollectStatistics() && !isCollectionForceDisabled()) {
      if (isAllowedByUserConsent.getAsBoolean() || isLocalStatisticsWithoutReport()) {
        return true;
      }
    }
    return false;
  }

  public static boolean isAllowedByUserConsent() {
    UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();
    if (settings != null && settings.isAllowed()) {
      return true;
    }

    ConsentOptionsProvider consentsProvider = ApplicationManager.getApplication().getService(ConsentOptionsProvider.class);
    if (consentsProvider != null && consentsProvider.isActivatedWithFreeLicense()) {
      return true;
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

  public static @NlsContexts.DetailedDescription @Nullable String getConsentWarning() {
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
        getEventLogProvider(recorderId).getEventLogSystemLogger$intellij_platform_statistics()
          .logFilesSend(totalLocalFiles, success, failed, false, successfullySentFiles, errors);
      }
    };

    return new EventLogStatisticsService(
      EventLogInternalSendConfig.createByRecorder(recorderId, true),
      new EventLogInternalApplicationInfo(isUseTestStatisticsConfig(), isUseTestStatisticsSendEndpoint()),
      listener
    );
  }

  public static EventLogSettingsClient createExternalSettings(@NotNull String recorderId, boolean isTestConfig, boolean isTestSendEndpoint, long cacheTimeoutMs) {
    return new EventLogUploadSettingsClient(recorderId, new EventLogInternalApplicationInfo(isTestConfig, isTestSendEndpoint), cacheTimeoutMs);
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
