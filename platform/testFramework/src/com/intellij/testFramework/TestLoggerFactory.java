/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.xml.DOMConfigurator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class TestLoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOG_DIR_MACRO = "$LOG_DIR$";
  private static final String LOG_DIR = "testlog";
  private static final long LOG_SIZE_LIMIT = 100 * 1024 * 1024;
  private static final long LOG_SEEK_WINDOW = 100 * 1024;

  private boolean myInitialized = false;

  private TestLoggerFactory() { }

  @Override
  public synchronized Logger getLoggerInstance(final String name) {
    if (!myInitialized) {
      init();
    }

    return new TestLogger(org.apache.log4j.Logger.getLogger(name));
  }

  private void init() {
    try {
      File logXmlFile = new File(PathManager.getHomePath(), "test-log.xml");
      if (!logXmlFile.exists()) {
        logXmlFile = new File(PathManager.getBinPath(), "log.xml");
      }
      if (!logXmlFile.exists()) {
        return;
      }

      final String logDir = getTestLogDir();
      String text = FileUtil.loadFile(logXmlFile);
      text = StringUtil.replace(text, SYSTEM_MACRO, StringUtil.replace(PathManager.getSystemPath(), "\\", "\\\\"));
      text = StringUtil.replace(text, APPLICATION_MACRO, StringUtil.replace(PathManager.getHomePath(), "\\", "\\\\"));
      text = StringUtil.replace(text, LOG_DIR_MACRO, StringUtil.replace(logDir, "\\", "\\\\"));

      final File logDirFile = new File(logDir);
      if (!logDirFile.mkdirs() && !logDirFile.exists()) {
        throw new IOException("Unable to create log dir: " + logDirFile);
      }

      System.setProperty("log4j.defaultInitOverride", "true");
      final DOMConfigurator domConfigurator = new DOMConfigurator();
      try {
        domConfigurator.doConfigure(new StringReader(text), LogManager.getLoggerRepository());
      }
      catch (ClassCastException e) {
        // shit :-E
        System.err.println("log.xml content:\n" + text);
        throw e;
      }

      File ideaLog = new File(getTestLogDir(), "idea.log");
      if (ideaLog.exists() && ideaLog.length() >= LOG_SIZE_LIMIT) {
        FileUtil.writeToFile(ideaLog, "");
      }

      myInitialized = true;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static String getTestLogDir() {
    return PathManager.getSystemPath() + "/" + LOG_DIR;
  }

  public static void dumpLogToStdout(@NotNull String testStartMarker) {
    File ideaLog = new File(getTestLogDir(), "idea.log");
    if (ideaLog.exists()) {
      try {
        long length = ideaLog.length();
        String logText;

        if (length > LOG_SEEK_WINDOW) {
          RandomAccessFile file = new RandomAccessFile(ideaLog, "r");
          try {
            file.seek(length - LOG_SEEK_WINDOW);
            byte[] bytes = new byte[(int)LOG_SEEK_WINDOW];
            int read = file.read(bytes);
            logText = new String(bytes, 0, read);
          }
          finally {
            file.close();
          }
        }
        else {
          logText = FileUtil.loadFile(ideaLog);
        }

        Pattern logStart = Pattern.compile("[0-9\\-, :\\[\\]]+(DEBUG|INFO|ERROR) - ");
        System.out.println("\n\nIdea Log:");
        for (String line : StringUtil.splitByLines(logText.substring(Math.max(0, logText.lastIndexOf(testStartMarker))))) {
          Matcher matcher = logStart.matcher(line);
          int lineStart = matcher.lookingAt() ? matcher.end() : 0;
          System.out.println(line.substring(lineStart));
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void enableDebugLogging(@NotNull Disposable parentDisposable, String... categories) {
    for (String category : categories) {
      final Logger logger = Logger.getInstance(category);
      logger.setLevel(Level.DEBUG);
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          logger.setLevel(Level.INFO);
        }
      });
    }
  }
}