// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType;
import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateError;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public final class EventLogSystemLogger {
  public static final String DEFAULT_RECORDER = "FUS";
  public static final String GROUP = "event.log";

  public static void logMetadataLoad(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "metadata.loaded", data);
  }

  public static void logMetadataUpdated(@NotNull String recorderId, @Nullable String version) {
    final FeatureUsageData data = new FeatureUsageData().addVersionByString(version);
    logEvent(recorderId, "metadata.updated", data);
  }

  public static void logMetadataErrorOnLoad(@NotNull String recorderId, @NotNull EventLogMetadataUpdateError error) {
    logMetadataError(recorderId, "metadata.load.failed", error);
  }

  public static void logMetadataErrorOnUpdate(@NotNull String recorderId, @NotNull EventLogMetadataUpdateError error) {
    logMetadataError(recorderId, "metadata.update.failed", error);
  }

  private static void logMetadataError(@NotNull String recorderId, @NotNull String eventId, @NotNull EventLogMetadataUpdateError error) {
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
                                  @NotNull List<String> successfullySentFiles,
                                  @NotNull List<Integer> errors) {
    EventLogRecorderConfiguration config = EventLogConfiguration.getInstance().getOrCreate(recorderId);
    final FeatureUsageData data = new FeatureUsageData().
      addData("total", total).
      addData("send", succeed + failed).
      addData("succeed", succeed).
      addData("failed", failed).
      addData("errors", ContainerUtil.map(errors, error -> String.valueOf(error))).
      addData("external", external).
      addData("paths", ContainerUtil.map(successfullySentFiles, path -> config.anonymize(path)));
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

  public static void logCreatingExternalSendCommand(@NotNull List<String> recorders) {
    for (String recorderId : recorders) {
      logEvent(recorderId, "external.send.command.creation.started");
    }
  }

  public static void logFinishedCreatingExternalSendCommand(@NotNull List<String> recorders, @Nullable EventLogUploadErrorType errorType) {
    boolean succeed = errorType == null;
    FeatureUsageData data = new FeatureUsageData().addData("succeed", succeed);
    if (!succeed) {
      data.addData("error", errorType.name());
    }

    for (String recorderId : recorders) {
      logEvent(recorderId, "external.send.command.creation.finished", data);
    }
  }

  public static void logSystemError(@NotNull String recorderId, @NotNull String eventId, @NotNull String errorClass, long time) {
    FeatureUsageData data = new FeatureUsageData().addData("error", errorClass);
    if (time != -1) {
      data.addData("error_ts", time);
    }
    logEvent(recorderId, eventId, data);
  }

  private static void logEvent(@NotNull String recorderId, @NotNull String eventId, @NotNull FeatureUsageData data) {
    StatisticsEventLoggerProvider provider = StatisticsEventLogProviderUtil.getEventLogProvider(recorderId);
    String groupId = getGroupId(recorderId);
    provider.getLogger().logAsync(new EventLogGroup(groupId, provider.getVersion()), eventId, data.build(), false);
  }

  private static void logEvent(@NotNull String recorderId, @NotNull String eventId) {
    StatisticsEventLoggerProvider provider = StatisticsEventLogProviderUtil.getEventLogProvider(recorderId);
    String groupId = getGroupId(recorderId);
    provider.getLogger().logAsync(new EventLogGroup(groupId, provider.getVersion()), eventId, false);
  }

  private static String getGroupId(@NotNull String recorderId) {
    if (DEFAULT_RECORDER.equals(recorderId)) {
      return GROUP;
    }
    return StringUtil.toLowerCase(recorderId) + "." + GROUP;
  }
}
