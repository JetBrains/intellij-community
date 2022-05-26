// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.application.PathManager.PROPERTY_LOG_PATH;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public final class TestLoggerFactory implements Logger.Factory {
  /**
   * If property is {@code true}, saves full test log to a separate file, instead of flushing it in the stdout (buildlog on TC)
   */
  private static final String SPLIT_TEST_LOGS_KEY = "idea.split.test.logs";
  private static final String SPLIT_LOGS_SUBDIR = "splitTestLogs";
  private static boolean SPLIT_TEST_LOGS = SystemProperties.getBooleanProperty(SPLIT_TEST_LOGS_KEY, false);
  private static final String SYSTEM_MACRO = "$SYSTEM_DIR$";
  private static final String APPLICATION_MACRO = "$APPLICATION_DIR$";
  private static final String LOG_DIR_MACRO = "$LOG_DIR$";
  private static final String LOG_DIR = "testlog";
  private static final long LOG_SIZE_LIMIT = 100 * 1024 * 1024;
  private static final long LOG_SEEK_WINDOW = 100 * 1024;

  private boolean myInitialized;

  private TestLoggerFactory() { }

  @Override
  public synchronized @NotNull Logger getLoggerInstance(@NotNull String category) {
    if (!myInitialized && reconfigure()) {
      myInitialized = true;
    }

    return new TestLogger(category);
  }

  /**
   * @return true iff logs for each test should be saved separately
   */
  static boolean shouldSplitTestLogs() {
    return SPLIT_TEST_LOGS;
  }

  public static boolean reconfigure() {
    try {
      String customConfigPath = System.getProperty(PathManager.PROPERTY_LOG_CONFIG_FILE);
      Path logProperties = customConfigPath != null
                        ? Paths.get(customConfigPath)
                        : Paths.get(PathManager.getHomePath(), "test-log.properties");
      if (Files.exists(logProperties)) {
        try (final InputStream in = Files.newInputStream(logProperties)) {
          final BufferedInputStream bin = new BufferedInputStream(in);
          LogManager.getLogManager().readConfiguration(bin);
        }
      }

      String logDir = getTestLogDir();
      Files.createDirectories(Paths.get(logDir));

      Path logFile = Paths.get(getTestLogDir(), "idea.log");
      JulLogger.clearHandlers();
      JulLogger.configureLogFileAndConsole(logFile, false, false, null);

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
      if (!logger.isDebugEnabled()) {
        logger.setLevel(LogLevel.DEBUG);
        Disposer.register(parentDisposable, () -> logger.setLevel(LogLevel.INFO));
      }
    }
  }

  private static final char FAILED_TEST_DEBUG_OUTPUT_MARKER = '\u2003';  // used in `FailedTestDebugLogConsoleFolding#shouldFoldLine`
  private static final StringBuilder BUFFER = new StringBuilder();
  private static final int MAX_BUFFER_LENGTH = 10_000_000;

  static void log(@NotNull String level, @NotNull String category, @Nullable String message, @Nullable Throwable t) {
    StringWriter writer = new StringWriter(t == null ? 256 : 4096);

    String source = category.substring(Math.max(category.length() - 30, 0));
    writer.write(String.format("%1$tH:%1$tM:%1$tS,%1$tL %2$-5s %3$30s - ", System.currentTimeMillis(), level, source));
    writer.write(message != null ? message : "");
    writer.write(System.lineSeparator());
    if (t != null) {
      t.printStackTrace(new PrintWriter(writer));
      writer.write(System.lineSeparator());
    }

    synchronized (BUFFER) {
      BUFFER.append(writer.getBuffer());
      if (BUFFER.length() > MAX_BUFFER_LENGTH) {
        BUFFER.delete(0, BUFFER.length() - MAX_BUFFER_LENGTH + MAX_BUFFER_LENGTH / 4);
      }
    }
  }

  public static void onTestStarted() {
    // clear buffer from tests which failed to report their termination properly
    synchronized (BUFFER) {
      BUFFER.setLength(0);
    }
  }

  private static @NotNull String saveSplitLog(@NotNull String testName, @NotNull String buffer) {
    var logsDir = new File(getTestLogDir(), SPLIT_LOGS_SUBDIR);
    if (!logsDir.exists() && !logsDir.mkdirs()) {
      buffer += "\nUnable to create dir for split logs, disabling splitting: " + logsDir;
      SPLIT_TEST_LOGS = false;
      return buffer;
    }

    var testFileName = FileUtil.sanitizeFileName(testName);
    var logFile = FileUtil.findSequentNonexistentFile(logsDir, testFileName, "log");

    try (var writer = new BufferedWriter(new FileWriter(logFile, StandardCharsets.UTF_8))) {
      writer.write(buffer);
    }
    catch (IOException e) {
      buffer += "\nError writing split log, disabling splitting: " + logFile + "\n" + e;
      SPLIT_TEST_LOGS = false;
      return buffer;
    }
    return "Log saved to: " + logFile.getName() + " (" + logFile + ")";
  }

  /**
   * @deprecated use {@link #onTestFinished(boolean, Description)} or {@link #onTestFinished(boolean, String)} instead
   */
  @Deprecated
  public static void onTestFinished(boolean success) {
    onTestFinished(success, "unnamed_test");
  }

  /**
   * @see #onTestFinished(boolean, String)
   */
  public static void onTestFinished(boolean success, @NotNull Description description) {
    onTestFinished(success, description.getDisplayName());
  }

  public static void logTestFailure(@NotNull Throwable t) {
    if (shouldSplitTestLogs()) {
      log(LogLevel.ERROR.toString(), "Test framework", "Test failed", t);
    }
  }

  /**
   * Invoke this method instead of {@link #onTestFinished(boolean)} to support separate logs saving
   *
   * @param testName - going to be used for the log file name
   */
  public static void onTestFinished(boolean success, @NotNull String testName) {
    String buffer;
    synchronized (BUFFER) {
      buffer = BUFFER.length() != 0 && !success ? BUFFER.toString() : null;
      BUFFER.setLength(0);
    }
    if (buffer != null) {
      if (shouldSplitTestLogs()) {
        buffer = saveSplitLog(testName, buffer);
      }

      if (System.getenv("TEAMCITY_VERSION") != null) {
        // print in several small statements to avoid service messages tearing causing this fold to expand
        // using .out instead of .err by the advice from Nikita Skvortsov
        System.out.flush();
        System.out.println("##teamcity[blockOpened name='DEBUG log']");
        System.out.flush();
        System.out.println(buffer);
        System.out.flush();
        System.out.println("##teamcity[blockClosed name='DEBUG log']");
        System.out.flush();
      }
      else {
        // mark each line in IDEA console with this hidden mark to be able to fold it automatically
        List<String> lines = LineTokenizer.tokenizeIntoList(buffer, false, false);
        if (!lines.get(0).startsWith("\n")) lines.set(0, "\n" + lines.get(0));
        System.err.println(String.join(FAILED_TEST_DEBUG_OUTPUT_MARKER + "\n", lines));
      }
    }
  }

  public static @NotNull TestRule createTestWatcher() {
    return new TestWatcher() {
      @Override
      protected void succeeded(Description description) {
        onTestFinished(true, description);
      }

      @Override
      protected void failed(Throwable e, Description description) {
        logTestFailure(e);
        onTestFinished(false, description);
      }

      @Override
      protected void skipped(AssumptionViolatedException e, Description description) {
        onTestFinished(true, description);
      }

      @Override
      protected void starting(@NotNull Description d) {
        onTestStarted();
      }
    };
  }
}
