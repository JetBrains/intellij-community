// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog;

import com.intellij.internal.statistic.eventLog.connection.metadata.EventLogMetadataUpdateError;
import com.intellij.internal.statistic.eventLog.uploader.EventLogUploadException.EventLogUploadErrorType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil.getEventLogProvider;

@ApiStatus.Internal
public final class EventLogSystemLogger {
  public static final String GROUP = "fus.event.log";

  public static void logMetadataLoad(@NotNull String recorderId, @Nullable String version) {
    getEventLogSystemCollector(recorderId).logMetadataLoaded(version);
  }

  public static void logMetadataUpdated(@NotNull String recorderId, @Nullable String version) {
    getEventLogSystemCollector(recorderId).logMetadataUpdated(version);
  }

  public static void logMetadataErrorOnLoad(@NotNull String recorderId, @NotNull EventLogMetadataUpdateError error) {
    getEventLogSystemCollector(recorderId).logMetadataLoadFailed(error);
  }

  public static void logMetadataErrorOnUpdate(@NotNull String recorderId, @NotNull EventLogMetadataUpdateError error) {
    getEventLogSystemCollector(recorderId).logMetadataUpdateFailed(error);
  }

  public static void logFilesSend(@NotNull String recorderId,
                                  int total,
                                  int succeed,
                                  int failed,
                                  boolean external,
                                  @NotNull List<String> successfullySentFiles,
                                  @NotNull List<Integer> errors) {
    getEventLogSystemCollector(recorderId).logFilesSend(total, succeed, failed, external, successfullySentFiles, errors);
  }

  public static void logStartingExternalSend(@NotNull String recorderId, long time) {
    getEventLogSystemCollector(recorderId).logStartingExternalSend(time);
  }

  public static void logFinishedExternalSend(@NotNull String recorderId, @Nullable String error, long time) {
    getEventLogSystemCollector(recorderId).logExternalSendFinished(error, time);
  }

  public static void logCreatingExternalSendCommand(@NotNull List<String> recorders) {
    for (String recorderId : recorders) {
      getEventLogSystemCollector(recorderId).logExternalSendCommandCreationStarted();
    }
  }

  public static void logFinishedCreatingExternalSendCommand(@NotNull List<String> recorders, @Nullable EventLogUploadErrorType errorType) {
    for (String recorderId : recorders) {
      getEventLogSystemCollector(recorderId).logExternalSendCommandCreationFinished(errorType);
    }
  }

  public static void logLoadingConfigFailed(@NotNull String recorderId, @NotNull String errorClass, long time) {
    getEventLogSystemCollector(recorderId).logLoadingConfigFailed(errorClass, time);
    FeatureUsageData data = new FeatureUsageData(recorderId).addData("error", errorClass);
    if (time != -1) {
      data.addData("error_ts", time);
    }
  }

  private static EventLogSystemCollector getEventLogSystemCollector(String recorderId) {
    return getEventLogProvider(recorderId).getEventLogSystemCollector();
  }
}
