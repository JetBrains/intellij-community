/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

public class TeamCityLogger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.TeamCityLogger");

  private static final boolean isUnderTC = System.getProperty("bootstrap.testcases") != null;

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
