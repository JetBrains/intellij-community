// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private static final int CURRENT_VERSION = 1;

  @SuppressWarnings("NonConstantLogger") private final @NonNls Logger myLogger;

  public ExternalEventsLogger() {
    myLogger = Logger.getLogger("com.intellij.internal.statistic.uploader.events");
    String logDirectory = findDirectory(1_000_000L);
    if (logDirectory != null) {
      myLogger.addHandler(newAppender(getEventLogFile(logDirectory, CURRENT_VERSION).getAbsolutePath()));
      myLogger.setLevel(Level.ALL);
    }
  }

  public static @NotNull Handler newAppender(@NotNull String logPath) {
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

  private static @NotNull File getEventLogFile(@NotNull String logDirectory, int version) {
    return new File(logDirectory, "idea_statistics_uploader_events_v" + version + ".log");
  }

  public void logSendingLogsStarted() {
    logEvent(new ExternalUploadStartedEvent(System.currentTimeMillis(), null));
  }

  public void logSendingLogsFinished(@NotNull StatisticsResult.ResultCode code) {
    logEvent(new ExternalUploadFinishedEvent(System.currentTimeMillis(), code.name(), null));
  }

  public void logSendingLogsFinished(@NotNull String recorderId, @NotNull String error) {
    logEvent(new ExternalUploadFinishedEvent(System.currentTimeMillis(), error, recorderId));
  }

  public void logSendingLogsFinished(@NotNull String recorderId, @NotNull StatisticsResult.ResultCode code) {
    String error = code == StatisticsResult.ResultCode.SEND ? null : code.name();
    logEvent(new ExternalUploadFinishedEvent(System.currentTimeMillis(), error, recorderId));
  }

  public void logSendingLogsSucceed(@NotNull String recorderId,
                                    @NotNull List<String> successfullySentFiles,
                                    @NotNull List<Integer> errors,
                                    int total) {
    int succeed = successfullySentFiles.size();
    int failed = errors.size();
    logEvent(new ExternalUploadSendEvent(System.currentTimeMillis(), succeed, failed, total, successfullySentFiles, errors, recorderId));
  }

  @Override
  public void logLoadingConfigFailed(@NotNull String recorderId, @NotNull Throwable exception) {
    logEvent(new ExternalSystemErrorEvent(System.currentTimeMillis(), exception, recorderId));
  }

  private void logEvent(@NotNull ExternalSystemEvent event) {
    myLogger.info(ExternalSystemEventSerializer.serialize(event));
  }

  public static @NotNull List<ExternalSystemEvent> parseEvents(@NotNull File directory) throws IOException {
    VersionedFile versionedFile = VersionedFile.find(directory.getAbsolutePath());
    List<String> lines = versionedFile.file.exists() ? Files.readAllLines(versionedFile.file.toPath()) : Collections.emptyList();
    if (!lines.isEmpty()) {
      List<ExternalSystemEvent> events = new ArrayList<>();
      for (String line : lines) {
        ExternalSystemEvent event = ExternalSystemEventSerializer.deserialize(line, versionedFile.version);
        if (event != null) {
          events.add(event);
        }
      }
      return events;
    }
    return Collections.emptyList();
  }

  private static class VersionedFile {
    protected final File file;
    protected final int version;

    private VersionedFile(@NotNull File file, int version) {
      this.file = file;
      this.version = version;
    }

    protected static @NotNull ExternalEventsLogger.VersionedFile find(@NotNull String logDirectory) {
      int version = 0;
      File file = new File(logDirectory, "idea_statistics_uploader_events.log");

      if (!file.exists()) {
        version++;
        file = getEventLogFile(logDirectory, version);
      }
      return new VersionedFile(file, version);
    }
  }
}
