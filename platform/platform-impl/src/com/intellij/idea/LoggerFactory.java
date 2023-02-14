// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.diagnostic.DialogAppender;
import com.intellij.diagnostic.JsonLogHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.logging.Level;

public final class LoggerFactory implements Logger.Factory {
  public static final String LOG_FILE_NAME = "idea.log";

  public static @NotNull Path getLogFilePath() {
    return PathManager.getLogDir().resolve(LOG_FILE_NAME);
  }

  public LoggerFactory() {
    JulLogger.clearHandlers();
    java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
    rootLogger.setLevel(Level.INFO);

    boolean logToJsonStdout = Boolean.getBoolean("intellij.log.to.json.stdout");
    if (logToJsonStdout) {
      System.setProperty("intellij.log.stdout", "false");
      JsonLogHandler jsonLogHandler = new JsonLogHandler();
      rootLogger.addHandler(jsonLogHandler);
    }

    JulLogger.configureLogFileAndConsole(getLogFilePath(), true, true, !logToJsonStdout, () -> IdeaLogger.dropFrequentExceptionsCaches());

    DialogAppender dialogAppender = new DialogAppender();
    dialogAppender.setLevel(Level.SEVERE);
    rootLogger.addHandler(dialogAppender);
  }

  @Override
  public @NotNull Logger getLoggerInstance(@NotNull String name) {
    return new IdeaLogger(java.util.logging.Logger.getLogger(name));
  }
}
