// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.uploader.events;

import com.intellij.internal.statistic.eventLog.DataCollectorSystemEventLogger;
import com.intellij.internal.statistic.eventLog.connection.StatisticsResult;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.*;

import static com.intellij.internal.statistic.uploader.ExternalDataCollectorLogger.findDirectory;

public class ExternalEventsLogger implements DataCollectorSystemEventLogger {
  @SuppressWarnings("NonConstantLogger") @NonNls private final Logger myLogger;

  public ExternalEventsLogger() {
    myLogger = Logger.getLogger("com.intellij.internal.statistic.uploader.events");
    String logDirectory = findDirectory(1_000_000L);
    if (logDirectory != null) {
      myLogger.addHandler(newAppender(getEventLogFile(logDirectory).getAbsolutePath()));
      myLogger.setLevel(Level.ALL);
    }
  }

  @NotNull
  public static Handler newAppender(@NotNull String logPath) {
    try {
      @NonNls FileHandler appender = new FileHandler(logPath, false);
      appender.setFormatter(new Formatter() {
        @Override
        public String format(LogRecord record) {
          return record.getMessage() + "\n";
        }
      });
      appender.setLevel(Level.ALL);
      return appender;
    }
    catch (IOException e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Error creating log file: " + e.getMessage());
      return new ConsoleHandler();
    }
  }

  @NotNull
  private static File getEventLogFile(@NotNull String logDirectory) {
    return new File(logDirectory, "idea_statistics_uploader_events.log");
  }

  public void logSendingLogsStarted() {
    logEvent(new ExternalUploadStartedEvent(System.currentTimeMillis()));
  }

  public void logSendingLogsFinished(@NotNull String error) {
    logEvent(new ExternalUploadFinishedEvent(System.currentTimeMillis(), error));
  }

  public void logSendingLogsFinished(@NotNull StatisticsResult.ResultCode code) {
    String error = code == StatisticsResult.ResultCode.SEND ? null : code.name();
    logEvent(new ExternalUploadFinishedEvent(System.currentTimeMillis(), error));
  }

  public void logSendingLogsSucceed(@NotNull List<String> successfullySentFiles, @NotNull List<Integer> errors, int total) {
    int succeed = successfullySentFiles.size();
    int failed = errors.size();
    logEvent(new ExternalUploadSendEvent(System.currentTimeMillis(), succeed, failed, total, successfullySentFiles, errors));
  }

  @Override
  public void logErrorEvent(@NotNull String eventId, @NotNull Throwable exception) {
    logEvent(new ExternalSystemErrorEvent(System.currentTimeMillis(), eventId, exception));
  }

  private void logEvent(@NotNull ExternalSystemEvent event) {
    myLogger.info(ExternalSystemEventSerializer.serialize(event));
  }

  @NotNull
  public static List<ExternalSystemEvent> parseEvents(@NotNull File directory) throws IOException {
    File file = getEventLogFile(directory.getAbsolutePath());
    List<String> lines = file.exists() ? Files.readAllLines(file.toPath()) : Collections.emptyList();
    if (!lines.isEmpty()) {
      List<ExternalSystemEvent> events = new ArrayList<>();
      for (String line : lines) {
        ExternalSystemEvent event = ExternalSystemEventSerializer.deserialize(line);
        if (event != null) {
          events.add(event);
        }
      }
      return events;
    }
    return Collections.emptyList();
  }
}
