// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.diagnostic.DialogAppender;
import com.intellij.diagnostic.JsonLogHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Locale;
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

    var consoleLogLevel = LogLevel.OFF;
    if (Boolean.getBoolean("intellij.log.to.json.stdout")) {
      System.setProperty("intellij.log.stdout", "false");
      rootLogger.addHandler(new JsonLogHandler());
    }
    else {
      var consoleLogLevelValue = System.getProperty("intellij.console.log.level");
      if (consoleLogLevelValue != null) {
        try {
          consoleLogLevel = LogLevel.valueOf(consoleLogLevelValue.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException _) { }
      }
      else if (AppMode.isRunningFromDevBuild() || Boolean.getBoolean("idea.log.console")) {
        consoleLogLevel = LogLevel.WARNING;
      }
    }

    var append = Boolean.parseBoolean(System.getProperty("idea.log.append", "true"));

    var writeAttachments = Boolean.parseBoolean(
      System.getProperty("idea.log.persist.attachments", System.getProperty(ApplicationManagerEx.IS_INTERNAL_PROPERTY))
    );

    JulLogger.configureStandardLoggers(
      consoleLogLevel, true, getLogFilePath(), append, writeAttachments, IdeaLogger::dropFrequentExceptionsCaches
    );

    if (!AppMode.isCommandLine() || ApplicationManagerEx.isInIntegrationTest()) {
      rootLogger.addHandler(new DialogAppender());
    }
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
