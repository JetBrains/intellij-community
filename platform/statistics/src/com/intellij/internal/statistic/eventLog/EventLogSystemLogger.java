// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType;
import com.intellij.internal.statistic.service.fus.EventLogWhitelistUpdateError;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class EventLogSystemLogger {
  private static final String GROUP = "event.log";

  public static void logWhitelistLoad(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "whitelist.loaded", data);
  }

  public static void logWhitelistUpdated(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "whitelist.updated", data);
  }

  public static void logWhitelistErrorOnLoad(@NotNull String recorderId, @NotNull EventLogWhitelistUpdateError error) {
    logWhitelistError(recorderId, "whitelist.load.failed", error);
  }

  public static void logWhitelistErrorOnUpdate(@NotNull String recorderId, @NotNull EventLogWhitelistUpdateError error) {
    logWhitelistError(recorderId, "whitelist.update.failed", error);
  }

  private static void logWhitelistError(@NotNull String recorderId, @NotNull String eventId, @NotNull EventLogWhitelistUpdateError error) {
    final FeatureUsageData data = new FeatureUsageData().
      addData("stage", error.getUpdateStage().name()).
      addData("error", error.getErrorType());

    int code = error.getErrorCode();
    if (code != -1) {
      data.addData("code", code);
    }
    logEvent(recorderId, eventId, data);
  }

  public static void logFilesSend(@NotNull String recorderId,
                                  int total,
                                  int succeed,
                                  int failed,
                                  boolean external,
                                  @NotNull List<String> successfullySentFiles) {
    final FeatureUsageData data = new FeatureUsageData().
      addData("total", total).
      addData("send", succeed + failed).
      addData("failed", failed).
      addData("external", external).
      addData("paths", ContainerUtil.map(successfullySentFiles, path -> EventLogConfiguration.INSTANCE.anonymize(path)));
    logEvent(recorderId, "logs.send", data);
  }

  public static void logStartingExternalSend(@NotNull String recorderId, long time) {
    FeatureUsageData data = new FeatureUsageData().addData("send_ts", time);
    logEvent(recorderId, "external.send.started", data);
  }

  public static void logFinishedExternalSend(@NotNull String recorderId, @Nullable String error, long time) {
    boolean succeed = StringUtil.isEmpty(error);
    FeatureUsageData data = new FeatureUsageData().addData("succeed", succeed).addData("send_ts", time);
    if (!succeed) {
      data.addData("error", error);
    }
    logEvent(recorderId, "external.send.finished", data);
  }

  public static void logCreatingExternalSendCommand(@NotNull String recorderId) {
    logEvent(recorderId, "external.send.command.creation.started");
  }

  public static void logFinishedCreatingExternalSendCommand(@NotNull String recorderId, @Nullable EventLogUploadErrorType errorType) {
    boolean succeed = errorType == null;
    FeatureUsageData data = new FeatureUsageData().addData("succeed", succeed);
    if (!succeed) {
      data.addData("error", errorType.name());
    }
    logEvent(recorderId, "external.send.command.creation.finished", data);
  }

  public static void logSystemError(@NotNull String recorderId, @NotNull String eventId, @NotNull String errorClass, long time) {
    FeatureUsageData data = new FeatureUsageData().addData("error", errorClass);
    if (time != -1) {
      data.addData("error_ts", time);
    }
    logEvent(recorderId, eventId, data);
  }

  private static void logEvent(@NotNull String recorderId, @NotNull String eventId, @NotNull FeatureUsageData data) {
    final StatisticsEventLoggerProvider provider = StatisticsEventLoggerKt.getEventLogProvider(recorderId);
    provider.getLogger().logAsync(new EventLogGroup(GROUP, provider.getVersion()), eventId, data.build(), false);
  }

  private static void logEvent(@NotNull String recorderId, @NotNull String eventId) {
    final StatisticsEventLoggerProvider provider = StatisticsEventLoggerKt.getEventLogProvider(recorderId);
    provider.getLogger().logAsync(new EventLogGroup(GROUP, provider.getVersion()), eventId, false);
  }
}
