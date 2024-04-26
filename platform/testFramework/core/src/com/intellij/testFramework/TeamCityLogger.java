// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public final class TeamCityLogger {
  private static final Logger LOG = Logger.getInstance(TeamCityLogger.class);

  public static final boolean isUnderTC = System.getenv("TEAMCITY_VERSION") != null;

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
    if (message.isEmpty()) return;
    try {
      while (message.charAt(0) == '\n') message = message.substring(1);
      if (level != null) message = level + ": " + message;
      if (!message.endsWith("\n")) message += "\n";
      FileUtil.appendToFile(reportFile(), message);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public static <T extends Throwable> void block(@NotNull String caption, @NotNull ThrowableRunnable<T> runnable) throws T {
    if (isUnderTC) {
      block(caption, () -> {
        runnable.run();
        return null;
      });
    }
    else {
      runnable.run();
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static <R, T extends Throwable> R block(@NotNull String caption, @NotNull ThrowableComputable<R, T> computable) throws T {
    if (!isUnderTC) {
      return computable.compute();
    }

    caption = escapeTeamcityServiceMessage(caption);

    // Printing in several small statements to avoid service messages tearing, causing the fold to expand.
    // Using .out instead of .err by the advice from Nikita Skvortsov.
    System.out.flush();
    System.out.println("##teamcity[blockOpened name='" + caption + "']");
    System.out.flush();
    try {
      return computable.compute();
    }
    finally {
      System.out.flush();
      System.out.println("##teamcity[blockClosed name='" + caption + "']");
      System.out.flush();
    }
  }

  private static @NotNull String escapeTeamcityServiceMessage(@NotNull String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char ch = s.charAt(i);
      char escape = switch (ch) {
        case '\n' -> 'n';
        case '\r' -> 'r';
        case '\'', '|', '[', ']' -> ch;
        default -> 0;
      };
      if (escape != 0) {
        sb.append('|').append(escape);
      }
      else if (ch < 0x20 /* space */ ||
               ch >= 0x7f /* DEL */) {
        sb.append(String.format("0x%04x", (short)ch));
      }
      else {
        sb.append(ch);
      }
    }
    return sb.toString();
  }
}
