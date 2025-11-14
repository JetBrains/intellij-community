// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import com.intellij.diagnostic.DialogAppender;
import com.intellij.diagnostic.JsonLogHandler;
import com.intellij.diagnostic.logs.LogLevelConfigurationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    var append = Boolean.parseBoolean(System.getProperty("idea.log.append", "true"));

    var writeAttachments = Boolean.parseBoolean(
      System.getProperty("idea.log.persist.attachments", System.getProperty(ApplicationManagerEx.IS_INTERNAL_PROPERTY))
    );

    JulLogger.configureLogFileAndConsole(
      getLogFilePath(), append, enableConsoleLogger, true, writeAttachments,
      () -> IdeaLogger.dropFrequentExceptionsCaches(), null, null
    );

    var dialogAppender = new DialogAppender();
    dialogAppender.setLevel(Level.SEVERE);
    rootLogger.addHandler(dialogAppender);

    configureLoggersFromSystemProperties();
  }

  private static void configureLoggersFromSystemProperties() {
    setLevelsForCategories(System.getProperty(LogLevelConfigurationManager.LOG_DEBUG_CATEGORIES_SYSTEM_PROPERTY), Level.FINE);
    setLevelsForCategories(System.getProperty(LogLevelConfigurationManager.LOG_TRACE_CATEGORIES_SYSTEM_PROPERTY), Level.FINER);
    setLevelsForCategories(System.getProperty(LogLevelConfigurationManager.LOG_ALL_CATEGORIES_SYSTEM_PROPERTY), Level.ALL);
  }

  private static void setLevelsForCategories(@Nullable String categories, Level level) {
    if (categories == null) {
      return;
    }
    var lastSeparator = -1;
    for (int i = 0; i <= categories.length(); i++) {
      if (i == categories.length() || categories.charAt(i) == ',' || categories.charAt(i) == '#') {
        if (lastSeparator + 1 < i) {
          var category = categories.substring(lastSeparator + 1, i);
          java.util.logging.Logger.getLogger(category).setLevel(level);
          java.util.logging.Logger.getLogger('#' + category).setLevel(level);
        }
        lastSeparator = i;
      }
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
