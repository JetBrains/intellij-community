// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  @Override
  public synchronized @NotNull Logger getLoggerInstance(@NotNull String name) {
    if (!myInitialized && reconfigure()) {
      myInitialized = true;
    }

    return new TestLogger(org.apache.log4j.Logger.getLogger(name));
  }

  public static boolean reconfigure() {
    try {
      Path logXmlFile = Paths.get(PathManager.getHomePath(), "test-log.xml");
      if (!Files.exists(logXmlFile)) {
        return false;
      }

      String logDir = getTestLogDir();
      String text = Files.readString(logXmlFile);
      text = StringUtil.replace(text, SYSTEM_MACRO, StringUtil.replace(PathManager.getSystemPath(), "\\", "\\\\"));
      text = StringUtil.replace(text, APPLICATION_MACRO, StringUtil.replace(PathManager.getHomePath(), "\\", "\\\\"));
      text = StringUtil.replace(text, LOG_DIR_MACRO, StringUtil.replace(logDir, "\\", "\\\\"));
      Files.createDirectories(Paths.get(logDir));

      System.setProperty("log4j.defaultInitOverride", "true");
      new DOMConfigurator().doConfigure(new StringReader(text), LogManager.getLoggerRepository());

      Path logFile = Paths.get(getTestLogDir(), "idea.log");
      if (Files.exists(logFile) && Files.size(logFile) >= LOG_SIZE_LIMIT) {
        Files.writeString(logFile, "");
      }

      return true;
    }
    catch (Throwable e) {
      e.printStackTrace();
      return false;
    }
  }

  public static String getTestLogDir() {
    String property = System.getProperty(PROPERTY_LOG_PATH);
    return property != null ? property : PathManager.getSystemPath() + '/' + LOG_DIR;
  }

  public static void dumpLogToStdout(@NotNull String testStartMarker) {
    Path logFile = Paths.get(getTestLogDir(), "idea.log");
    if (Files.exists(logFile)) {
      try {
        long length = Files.size(logFile);
        String logText;

        if (length > LOG_SEEK_WINDOW) {
          try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            file.seek(length - LOG_SEEK_WINDOW);
            byte[] bytes = new byte[(int)LOG_SEEK_WINDOW];
            int read = file.read(bytes);
            logText = new String(bytes, 0, read, StandardCharsets.UTF_8);
          }
        }
        else {
          logText = Files.readString(logFile);
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
      Logger logger = Logger.getInstance(category);
      logger.setLevel(Level.DEBUG);
      Disposer.register(parentDisposable, () -> logger.setLevel(Level.INFO));
    }
  }

  static final char FAILED_TEST_DEBUG_OUTPUT_MARKER = '\u2003';

  private static final StringWriter STRING_WRITER = new StringWriter();
  private static final StringBuffer BUFFER = STRING_WRITER.getBuffer();
  /** <b>NOTE:</b> inserted Unicode whitespace to be able to tell these failed tests log lines from the others and fold them */
  private static final WriterAppender APPENDER = new WriterAppender(new PatternLayout("%d{HH:mm:ss,SSS} %p %.30c - %m%n"), STRING_WRITER);
  private static final int MAX_BUFFER_LENGTH = 10_000_000;

  static void log(@NotNull org.apache.log4j.Logger logger, @NotNull Level level, @Nullable String message, @Nullable Throwable t) {
    APPENDER.doAppend(new LoggingEvent(Category.class.getName(), logger, level, message, t));

    //noinspection DoubleCheckedLocking
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
      if (System.getenv("TEAMCITY_VERSION") != null) {
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

  public static @NotNull TestRule createTestWatcher() {
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
