// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
      String hashedFiles = filesToString(finished.getSuccessfullySentFiles());
      String errors = errorsToString(finished.getErrors());
      return prefix + " " + finished.getSucceed() + " " + finished.getFailed() + " " + finished.getTotal() +
             " " + hashedFiles + " " + errors;
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
    else if (type == ExternalSystemEventType.SEND && length >= 5 && length <= 7) {
      int succeed = parseInt(parts[2]);
      int failed = parseInt(parts[3]);
      int total = parseInt(parts[4]);
      List<String> sentFiles = length >= 6 ? parseSentFiles(parts[5]) : Collections.emptyList();
      List<Integer> errors = length >= 7 ? parseErrors(parts[6]) : Collections.emptyList();
      return new ExternalUploadSendEvent(timestamp, succeed, failed, total, sentFiles, errors);
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

  @NotNull
  private static List<String> parseSentFiles(@NotNull String part) {
    return parseValues(part, value -> new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8));
  }

  @NotNull
  private static List<Integer> parseErrors(@NotNull String part) {
    return parseValues(part, value -> parseInt(value));
  }

  @NotNull
  private static <V> List<V> parseValues(@NotNull String part, @NotNull Function<String, V> processor) {
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

  @NotNull
  private static String filesToString(@NotNull List<String> files) {
    return valuesToString(files, path -> Base64.getEncoder().encodeToString(path.getBytes(StandardCharsets.UTF_8)));
  }

  @NotNull
  private static String errorsToString(@NotNull List<Integer> errors) {
    return valuesToString(errors, error -> String.valueOf(error));
  }

  private static <V> String valuesToString(@NotNull List<V> values, @NotNull Function<V, String> processor) {
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
