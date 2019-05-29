// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EventLogSystemLogger {
  private static final String GROUP = "event.log";

  public static void logWhitelistLoad(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "whitelist.loaded", data);
  }

  public static void logWhitelistUpdated(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "whitelist.updated", data);
  }

  public static void logFilesSend(@NotNull String recorderId, int total, int send, int failed) {
    final FeatureUsageData data = new FeatureUsageData().
      addData("total", total).
      addData("send", send).
      addData("failed", failed);
    logEvent(recorderId, "logs.send", data);
  }

  public static void logEvent(@NotNull String recorderId, @NotNull String eventId, @NotNull FeatureUsageData data) {
    final StatisticsEventLoggerProvider provider = StatisticsEventLoggerKt.getEventLogProvider(recorderId);
    provider.getLogger().log(new EventLogGroup(GROUP, provider.getVersion()), eventId, data.build(), false);
  }

  public static void logEvent(@NotNull String recorderId, @NotNull String eventId) {
    final StatisticsEventLoggerProvider provider = StatisticsEventLoggerKt.getEventLogProvider(recorderId);
    provider.getLogger().log(new EventLogGroup(GROUP, provider.getVersion()), eventId, false);
  }
}
