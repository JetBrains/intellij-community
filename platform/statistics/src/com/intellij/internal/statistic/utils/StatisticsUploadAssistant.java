// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils;

import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class StatisticsUploadAssistant {
  private static final String IDEA_SUPPRESS_REPORT_STATISTICS = "idea.suppress.statistics.report";
  private static final String ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT = "idea.local.statistics.without.report";

  public static final Object LOCK = new Object();

  private StatisticsUploadAssistant() {}

  public static boolean isSendAllowed() {
    if (Boolean.getBoolean(IDEA_SUPPRESS_REPORT_STATISTICS) || Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT)) {
      return false;
    }

    UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();
    return settings != null && settings.isAllowed();
  }

  public static boolean isCollectAllowed() {
    final UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();
    return (settings != null && settings.isAllowed()) || Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT);
  }

  public static boolean isTestStatisticsEnabled() {
    return Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT) || StringUtil.isNotEmpty(System.getenv("TEAMCITY_VERSION"));
  }

  public static @NotNull StatisticsService getEventLogStatisticsService(@NotNull String recorderId) {
    EventLogSendListener listener = new EventLogSendListener() {
      @Override
      public void onLogsSend(@NotNull List<String> successfullySentFiles, int failed, int totalLocalFiles) {
        EventLogSystemLogger.logFilesSend(recorderId, totalLocalFiles, successfullySentFiles.size(), failed, false, successfullySentFiles);
      }
    };

    return new EventLogStatisticsService(
      new DeviceConfiguration(EventLogConfiguration.INSTANCE.getDeviceId(), EventLogConfiguration.INSTANCE.getBucket()),
      new EventLogInternalRecorderConfig(recorderId),
      new EventLogInternalApplicationInfo(recorderId, false), listener
    );
  }

  public static EventLogUploadSettingsService createExternalSettings(@NotNull String recorderId, boolean isTest, long cacheTimeoutMs) {
    return new EventLogUploadSettingsService(recorderId, new EventLogInternalApplicationInfo(recorderId, isTest), cacheTimeoutMs);
  }
}
