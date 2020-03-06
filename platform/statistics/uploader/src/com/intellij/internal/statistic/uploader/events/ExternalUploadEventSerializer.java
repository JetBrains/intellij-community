// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader.events;

import com.intellij.internal.statistic.StatisticsEventLogUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExternalUploadEventSerializer {

  @NotNull
  public static String serialize(@NotNull ExternalUploadEvent event) {
    String prefix = event.getTimestamp() + " " + event.getEventType().name();
    if (event instanceof ExternalUploadFinishedEvent) {
      ExternalUploadFinishedEvent failed = (ExternalUploadFinishedEvent)event;
      String error = failed.getError();
      if (StatisticsEventLogUtil.isNotEmpty(error)) {
        return prefix + " " + error.replace(" ", "_");
      }
      return prefix;
    }
    else if (event instanceof ExternalUploadSendEvent) {
      ExternalUploadSendEvent finished = (ExternalUploadSendEvent)event;
      return prefix + " " + finished.getSucceed() + " " + finished.getFailed() + " " + finished.getTotal();
    }
    return prefix;
  }

  @Nullable
  public static ExternalUploadEvent deserialize(@NotNull String line) {
    String[] parts = line.split(" ");
    int length = parts.length;
    ExternalUploadEventType type = length > 1 ? ExternalUploadEventType.parse(parts[1]) : null;
    if (type == null) {
      return null;
    }

    long timestamp = parseLong(parts[0]);
    if (type == ExternalUploadEventType.FINISHED) {
      String error = parts.length >= 3 ? parts[2].trim() : null;
      return new ExternalUploadFinishedEvent(timestamp, error);
    }
    else if (type == ExternalUploadEventType.SEND && length == 5) {
      int succeed = parseInt(parts[2]);
      int failed = parseInt(parts[3]);
      int total = parseInt(parts[4]);
      return new ExternalUploadSendEvent(timestamp, succeed, failed, total);
    }
    else if (type == ExternalUploadEventType.STARTED && length == 2) {
      return new ExternalUploadStartedEvent(timestamp);
    }
    return null;
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  private static long parseLong(String value) {
    try {
      return Long.parseLong(value);
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }
}
