// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.testFramework;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public final class TeamCityLogger {
  private static final Logger LOG = Logger.getInstance(TeamCityLogger.class);

  public static final boolean isUnderTC = System.getProperty("bootstrap.testcases") != null;

  private TeamCityLogger() {}

  private static File reportFile() {
    return new File(PathManager.getHomePath() + "/reports/report.txt");
  }

  public static void info(String message) {
    if (isUnderTC) {
      tcLog(message, null);
    }
    else {
      LOG.info(message);
    }
  }

  public static void warning(String message) {
    warning(message, new Throwable());
  }
  public static void warning(String message, @Nullable Throwable throwable) {
    if (isUnderTC) {
      tcLog(message, "WARNING");
    } else {
      LOG.warn(message, throwable);
    }
  }

  public static void error(String message) {
    error(message, new Throwable());
  }
  public static void error(String message, @Nullable Throwable throwable) {
    if (isUnderTC) {
      tcLog(message, "ERROR");
    } else {
      LOG.error(message, throwable);
    }
  }

  private static void tcLog(String message, String level) {
    try {
      if (level != null) message = level + ": " + message;
      FileUtil.appendToFile(reportFile(), message + "\n");
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}
