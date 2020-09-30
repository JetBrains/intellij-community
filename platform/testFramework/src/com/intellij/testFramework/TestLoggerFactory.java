// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.log4j.*;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.xml.DOMConfigurator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.application.PathManager.PROPERTY_LOG_PATH;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public final class TestLoggerFactory implements Logger.Factory {
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOG_DIR_MACRO = "$LOG_DIR$";
  private static final String LOG_DIR = "testlog";
  private static final long LOG_SIZE_LIMIT = 100 * 1024 * 1024;
  private static final long LOG_SEEK_WINDOW = 100 * 1024;

  private boolean myInitialized;

  private TestLoggerFactory() { }

  @NotNull
  @Override
  public synchronized Logger getLoggerInstance(@NotNull final String name) {
    if (!myInitialized) {
      init();
    }

    return new TestLogger(org.apache.log4j.Logger.getLogger(name));
  }

  private void init() {
    if (!reconfigure()) return;
    myInitialized = true;
  }

  public static boolean reconfigure() {
    try {
      File logXmlFile = new File(PathManager.getHomePath(), "test-log.xml");

      if (!logXmlFile.exists()) {
        return false;
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
      try {
        final DOMConfigurator domConfigurator = new DOMConfigurator();
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

      return true;
    }
    catch (Throwable e) {
      e.printStackTrace();
      return false;
    }
  }

  public static String getTestLogDir() {
    if (System.getProperty(PROPERTY_LOG_PATH) != null) return System.getProperty(PROPERTY_LOG_PATH);

    return PathManager.getSystemPath() + "/" + LOG_DIR;
  }

  public static void dumpLogToStdout(@NotNull String testStartMarker) {
    File ideaLog = new File(getTestLogDir(), "idea.log");
    if (ideaLog.exists()) {
      try {
        long length = ideaLog.length();
        String logText;

        if (length > LOG_SEEK_WINDOW) {
          try (RandomAccessFile file = new RandomAccessFile(ideaLog, "r")) {
            file.seek(length - LOG_SEEK_WINDOW);
            byte[] bytes = new byte[(int)LOG_SEEK_WINDOW];
            int read = file.read(bytes);
            logText = new String(bytes, 0, read, StandardCharsets.UTF_8);
          }
        }
        else {
          logText = FileUtil.loadFile(ideaLog);
        }

        System.out.println("\n\nIdea Log:");
        Pattern logStart = Pattern.compile("[0-9\\-, :\\[\\]]+(DEBUG|INFO|ERROR) - ");
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

  public static void enableDebugLogging(@NotNull Disposable parentDisposable, String @NotNull ... categories) {
    for (String category : categories) {
      final Logger logger = Logger.getInstance(category);
      logger.setLevel(Level.DEBUG);
      Disposer.register(parentDisposable, () -> logger.setLevel(Level.INFO));
    }
  }

  private static final StringWriter STRING_WRITER = new StringWriter();
  private static final StringBuffer BUFFER = STRING_WRITER.getBuffer();
  static final char FAILED_TEST_DEBUG_OUTPUT_MARKER = '\u2003';
  // inserted unicode whitespace to be able to tell these failed tests log lines from the others and fold them
  private static final WriterAppender APPENDER = new WriterAppender(new PatternLayout("%d{HH:mm:ss,SSS} %p %.30c - %m%n"), STRING_WRITER);
  private static final int MAX_BUFFER_LENGTH = 10_000_000;
  private static final String CFQN = Category.class.getName();
  static void log(@NotNull org.apache.log4j.Logger logger, @NotNull Level level, @Nullable String message, @Nullable Throwable t) {
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      //return;
    }
    LoggingEvent event = new LoggingEvent(CFQN, logger, level, message, t);
    APPENDER.doAppend(event);

    if (BUFFER.length() > MAX_BUFFER_LENGTH) {
      synchronized (BUFFER) {
        if (BUFFER.length() > MAX_BUFFER_LENGTH) {
          BUFFER.delete(0, BUFFER.length() - MAX_BUFFER_LENGTH + MAX_BUFFER_LENGTH / 4);
        }
      }
    }
  }

  public static void onTestStarted() {
    // clear buffer from tests which failed to report their termination properly
    BUFFER.setLength(0);
  }
  public static void onTestFinished(boolean success) {
    if (!success && BUFFER.length() != 0) {
      if (UsefulTestCase.IS_UNDER_TEAMCITY) {
        // print in several small statements to avoid service messages tearing causing this fold to expand
        // using .out instead of .err by the advice from Nikita Skvortsov
        System.out.flush();
        System.out.println("##teamcity[blockOpened name='DEBUG log']\n");
        System.out.flush();
        System.out.println(BUFFER);
        System.out.flush();
        System.out.println("\n##teamcity[blockClosed name='DEBUG log']\n");
        System.out.flush();
      }
      else {
        // mark each line in IDEA console with this hidden mark to be able to fold it automatically
        String[] lines = LineTokenizer.tokenize(BUFFER, false, false);
        String text = StringUtil.join(lines, FAILED_TEST_DEBUG_OUTPUT_MARKER + "\n");
        if (!text.startsWith("\n")) text = "\n" + text;
        System.err.println(text);
      }
    }
    BUFFER.setLength(0);
  }

  @NotNull
  public static TestRule createTestWatcher() {
    return new TestWatcher() {
      @Override
      protected void succeeded(Description description) {
        onTestFinished(true);
      }

      @Override
      protected void failed(Throwable e, Description description) {
        onTestFinished(false);
      }

      @Override
      protected void skipped(AssumptionViolatedException e, Description description) {
        onTestFinished(true);
      }

      @Override
      protected void starting(@NotNull Description d) {
        onTestStarted();
      }
    };
  }
}