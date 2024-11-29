// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    var rootLogger = java.util.logging.Logger.getLogger("");
    rootLogger.setLevel(Level.INFO);

    var logToJsonStdout = Boolean.getBoolean("intellij.log.to.json.stdout");
    if (logToJsonStdout) {
      System.setProperty("intellij.log.stdout", "false");
      var jsonLogHandler = new JsonLogHandler();
      rootLogger.addHandler(jsonLogHandler);
    }

    var enableConsoleLogger = !logToJsonStdout && Boolean.parseBoolean(System.getProperty("idea.log.console", "true"));

    JulLogger.configureLogFileAndConsole(getLogFilePath(), true, enableConsoleLogger, true, () -> IdeaLogger.dropFrequentExceptionsCaches(), null, null);

    var dialogAppender = new DialogAppender();
    dialogAppender.setLevel(Level.SEVERE);
    rootLogger.addHandler(dialogAppender);
  }

  @Override
  public @NotNull Logger getLoggerInstance(@NotNull String name) {
    return new IdeaLogger(java.util.logging.Logger.getLogger(name));
  }

  public void flushHandlers() {
    for (var handler : java.util.logging.Logger.getLogger("").getHandlers()) {
      handler.flush();
    }
  }
}
