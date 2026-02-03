// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.config.StatisticsStringUtil.isNotEmpty;
import static com.intellij.internal.statistic.eventLog.StatisticsEventEscaper.escape;

public final class ExternalSystemEventSerializer {

  public static @NotNull String serialize(@NotNull ExternalSystemEvent event) {
    String prefix = event.getTimestamp() + " " + event.getEventType().name() + " " + event.getRecorderId();
    if (event instanceof ExternalUploadFinishedEvent failed) {
      if (isNotEmpty(failed.getError())) {
        return prefix + " " + escape(failed.getError());
      }
      return prefix;
    }
    else if (event instanceof ExternalUploadSendEvent finished) {
      String hashedFiles = filesToString(finished.getSuccessfullySentFiles());
      String errors = errorsToString(finished.getErrors());
      return prefix + " " + finished.getSucceed() + " " + finished.getFailed() + " " + finished.getTotal() +
             " " + hashedFiles + " " + errors;
    }
    else if (event instanceof ExternalSystemErrorEvent error) {
      return prefix + " " + escape(error.getErrorClass());
    }
    return prefix;
  }

  public static @Nullable ExternalSystemEvent deserialize(@NotNull String line, int version) {
    int payloadStartIndex = version == 0 ? 2 : 3;
    String[] parts = line.split(" ");
    int length = parts.length;
    if (length < payloadStartIndex) {
      return null;
    }

    ExternalSystemEventType type = ExternalSystemEventType.parse(parts[1]);
    if (type == null) {
      return null;
    }

    long timestamp = parseLong(parts[0]);
    String recorderId = version == 0 ? "FUS" : parts[2];
    if (type == ExternalSystemEventType.FINISHED) {
      String error = length > payloadStartIndex ? parts[payloadStartIndex].trim() : null;
      return new ExternalUploadFinishedEvent(timestamp, error, recorderId);
    }
    else if (type == ExternalSystemEventType.SEND && length > payloadStartIndex + 2) {
      int succeed = parseInt(parts[payloadStartIndex]);
      int failed = parseInt(parts[payloadStartIndex + 1]);
      int total = parseInt(parts[payloadStartIndex + 2]);
      List<String> sentFiles = length > payloadStartIndex + 3 ? parseSentFiles(parts[payloadStartIndex + 3]) : Collections.emptyList();
      List<Integer> errors = length > payloadStartIndex + 4 ? parseErrors(parts[payloadStartIndex + 4]) : Collections.emptyList();
      return new ExternalUploadSendEvent(timestamp, succeed, failed, total, sentFiles, errors, recorderId);
    }
    else if (type == ExternalSystemEventType.STARTED && length == payloadStartIndex) {
      return new ExternalUploadStartedEvent(timestamp, recorderId);
    }
    else if (type == ExternalSystemEventType.ERROR && length == payloadStartIndex + 1) {
      String errorClass = parts[payloadStartIndex].trim();
      return new ExternalSystemErrorEvent(timestamp, errorClass, recorderId);
    }
    return null;
  }

  private static @NotNull List<String> parseSentFiles(@NotNull String part) {
    return parseValues(part, value -> new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8));
  }

  private static @NotNull List<Integer> parseErrors(@NotNull String part) {
    return parseValues(part, value -> parseInt(value));
  }

  private static @NotNull <V> List<V> parseValues(@NotNull String part, @NotNull Function<? super String, ? extends V> processor) {
    try {
      if (part.startsWith("[") && part.endsWith("]")) {
        String unwrappedPart = part.substring(1, part.length() - 1);
        String[] values = unwrappedPart.split(",");
        return Arrays.stream(values)
          .filter(value -> !value.isEmpty())
          .map(processor)
          .collect(Collectors.toList());
      }
      return Collections.emptyList();
    }
    catch (IllegalArgumentException e) {
      return Collections.emptyList();
    }
  }

  private static @NotNull String filesToString(@NotNull List<String> files) {
    return valuesToString(files, path -> Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8)));
  }

  private static @NotNull String errorsToString(@NotNull List<Integer> errors) {
    return valuesToString(errors, error -> String.valueOf(error));
  }

  private static <V> String valuesToString(@NotNull List<? extends V> values, @NotNull Function<? super V, String> processor) {
    return values.stream().map(processor).collect(Collectors.joining(",", "[", "]"));
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
