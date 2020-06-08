// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.uploader.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.StatisticsStringUtil.isNotEmpty;
import static com.intellij.internal.statistic.eventLog.StatisticsEventEscaper.escape;

public final class ExternalSystemEventSerializer {

  @NotNull
  public static String serialize(@NotNull ExternalSystemEvent event) {
    String prefix = event.getTimestamp() + " " + event.getEventType().name();
    if (event instanceof ExternalUploadFinishedEvent) {
      ExternalUploadFinishedEvent failed = (ExternalUploadFinishedEvent)event;
      if (isNotEmpty(failed.getError())) {
        return prefix + " " + escape(failed.getError());
      }
      return prefix;
    }
    else if (event instanceof ExternalUploadSendEvent) {
      ExternalUploadSendEvent finished = (ExternalUploadSendEvent)event;
      String hashedFiles = finished.getSuccessfullySentFiles().stream()
        .map(path -> Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8)))
        .collect(Collectors.joining(",", "[", "]"));
      return prefix + " " + finished.getSucceed() + " " + finished.getFailed() + " " + finished.getTotal() + " " + hashedFiles;
    }
    else if (event instanceof ExternalSystemErrorEvent) {
      ExternalSystemErrorEvent error = (ExternalSystemErrorEvent)event;
      return prefix + " " + escape(error.getEvent()) + " " + escape(error.getErrorClass());
    }
    return prefix;
  }

  @Nullable
  public static ExternalSystemEvent deserialize(@NotNull String line) {
    String[] parts = line.split(" ");
    int length = parts.length;
    ExternalSystemEventType type = length > 1 ? ExternalSystemEventType.parse(parts[1]) : null;
    if (type == null) {
      return null;
    }

    long timestamp = parseLong(parts[0]);
    if (type == ExternalSystemEventType.FINISHED) {
      String error = parts.length >= 3 ? parts[2].trim() : null;
      return new ExternalUploadFinishedEvent(timestamp, error);
    }
    else if (type == ExternalSystemEventType.SEND && (length == 6 || length == 5)) {
      int succeed = parseInt(parts[2]);
      int failed = parseInt(parts[3]);
      int total = parseInt(parts[4]);
      List<String> sentFiles = length == 6 ? parseSentFiles(parts[5]) : Collections.emptyList();
      return new ExternalUploadSendEvent(timestamp, succeed, failed, total, sentFiles);
    }
    else if (type == ExternalSystemEventType.STARTED && length == 2) {
      return new ExternalUploadStartedEvent(timestamp);
    }
    else if (type == ExternalSystemEventType.ERROR && length == 4) {
      String event = parts[2].trim();
      String errorClass = parts[3].trim();
      return new ExternalSystemErrorEvent(timestamp, event, errorClass);
    }
    return null;
  }

  private static List<String> parseSentFiles(@NotNull String part) {
    try {
      if (part.startsWith("[") && part.endsWith("]")) {
        String unwrappedPart = part.substring(1, part.length() - 1);
        String[] filePathHashes = unwrappedPart.split(",");
        return Arrays.stream(filePathHashes)
          .filter(hash -> !hash.isEmpty())
          .map(hash -> new String(Base64.getDecoder().decode(hash), StandardCharsets.UTF_8))
          .collect(Collectors.toList());
      }
      return Collections.emptyList();
    }
    catch (IllegalArgumentException e) {
      return Collections.emptyList();
    }
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
