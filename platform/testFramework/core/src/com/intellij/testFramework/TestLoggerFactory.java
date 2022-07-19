// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.ComparisonFailure;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.LogManager;
import java.util.regex.Pattern;

import static com.intellij.openapi.application.PathManager.PROPERTY_LOG_PATH;
import static java.util.Objects.requireNonNullElse;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public final class TestLoggerFactory implements Logger.Factory {
  @SuppressWarnings("SpellCheckingInspection") private static final String LOG_DIR = "testlog";
  private static final String LOG_FILE_NAME = "idea.log";
  private static final String SPLIT_LOGS_SUBDIR = "splitTestLogs";

  private static final long LOG_SIZE_LIMIT = 100 * 1024 * 1024;
  private static final long LOG_SEEK_WINDOW = 100 * 1024;

  private static final char FAILED_TEST_DEBUG_OUTPUT_MARKER = '\u2003';  // used in `FailedTestDebugLogConsoleFolding#shouldFoldLine`
  private static final int MAX_BUFFER_LENGTH = 10_000_000;

  private final StringBuilder myBuffer = new StringBuilder();
  private boolean myInitialized;
  // when {@code true}, logs produced during a failed test are saved to a separate file instead of being dumped to the stdout
  private boolean mySplitTestLogs = Boolean.getBoolean("idea.split.test.logs");

  private TestLoggerFactory() { }

  @Override
  public synchronized @NotNull Logger getLoggerInstance(@NotNull String category) {
    if (!myInitialized && reconfigure()) {
      myInitialized = true;
    }

    return new TestLogger(category, this);
  }

  public static boolean reconfigure() {
    try {
      var customConfigPath = System.getProperty(PathManager.PROPERTY_LOG_CONFIG_FILE);
      var logProperties = customConfigPath != null ? Path.of(customConfigPath) : Path.of(PathManager.getHomePath(), "test-log.properties");
      if (Files.exists(logProperties)) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(logProperties))) {
          LogManager.getLogManager().readConfiguration(in);
        }
      }

      var logDir = getTestLogDir();
      Files.createDirectories(logDir);

      var logFile = logDir.resolve(LOG_FILE_NAME);
      JulLogger.clearHandlers();
      JulLogger.configureLogFileAndConsole(logFile, false, false, true, null);

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

  public static @NotNull Path getTestLogDir() {
    var property = System.getProperty(PROPERTY_LOG_PATH);
    return property != null ? Path.of(property) : Path.of(PathManager.getSystemPath(), LOG_DIR);
  }

  public static void dumpLogToStdout(@NotNull String testStartMarker) {
    var logFile = getTestLogDir().resolve(LOG_FILE_NAME);
    if (Files.exists(logFile)) {
      try {
        var length = Files.size(logFile);
        String logText;

        if (length > LOG_SEEK_WINDOW) {
          try (var file = new RandomAccessFile(logFile.toFile(), "r")) {
            file.seek(length - LOG_SEEK_WINDOW);
            var bytes = new byte[(int)LOG_SEEK_WINDOW];
            var read = file.read(bytes);
            logText = new String(bytes, 0, read, StandardCharsets.UTF_8);
          }
        }
        else {
          logText = Files.readString(logFile);
        }

        System.out.println("\n\nIdea Log:");
        var logStart = Pattern.compile("[\\d\\-, :\\[\\]]+(DEBUG|INFO|ERROR) - ");
        for (var line : StringUtil.splitByLines(logText.substring(Math.max(0, logText.lastIndexOf(testStartMarker))))) {
          var matcher = logStart.matcher(line);
          var lineStart = matcher.lookingAt() ? matcher.end() : 0;
          System.out.println(line.substring(lineStart));
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void enableDebugLogging(@NotNull Disposable parentDisposable, String @NotNull ... categories) {
    for (var category : categories) {
      var logger = Logger.getInstance(category);
      if (!logger.isDebugEnabled()) {
        logger.setLevel(LogLevel.DEBUG);
        Disposer.register(parentDisposable, () -> logger.setLevel(LogLevel.INFO));
      }
    }
  }

  private void buffer(LogLevel level, String category, @Nullable String message, @Nullable Throwable t) {
    var writer = new StringWriter(t == null ? 256 : 4096);

    var source = category.substring(Math.max(category.length() - 30, 0));
    writer.write(String.format("%1$tH:%1$tM:%1$tS,%1$tL %2$-5s %3$30s - ", System.currentTimeMillis(), level.getLevelName(), source));
    writer.write(message != null ? message : "");
    writer.write(System.lineSeparator());
    if (t != null) {
      t.printStackTrace(new PrintWriter(writer));
      writer.write(System.lineSeparator());
    }

    synchronized (myBuffer) {
      myBuffer.append(writer.getBuffer());
      if (myBuffer.length() > MAX_BUFFER_LENGTH) {
        myBuffer.delete(0, myBuffer.length() - MAX_BUFFER_LENGTH + MAX_BUFFER_LENGTH / 4);
      }
    }
  }

  /**
   * Report full contents, not limited to 20 characters of ComparisonFailure#MAX_CONTEXT_LENGTH
   */
  @Nullable
  private static String dumpComparisonFailures(@Nullable Throwable t) {
    if (t == null) return null;

    StringBuilder sb = new StringBuilder();
    ExceptionUtil.findCauseAndSuppressed(t, ComparisonFailure.class).forEach(e -> {
      logComparisonFailure(sb,
                           e.getExpected(),
                           e.getActual());
    });

    ExceptionUtil.findCauseAndSuppressed(t, junit.framework.ComparisonFailure.class).forEach(e -> {
      logComparisonFailure(sb,
                           e.getExpected(),
                           e.getActual());
    });

    return sb.length() != 0 ? sb.toString() : null;
  }

  private static void logComparisonFailure(@NotNull StringBuilder sb, @Nullable String expected, @Nullable String actual) {
    if (expected == null && actual == null) return;

    sb.append("Comparison Failure");
    sb.append(System.lineSeparator());

    if (actual == null) {
      sb.append("Actual [null]");
    }
    else {
      sb.append("Actual [[");
      sb.append(actual);
      sb.append("]]");
    }
    sb.append(System.lineSeparator());

    if (expected == null) {
      sb.append("Expected [null]");
    }
    else {
      sb.append("Expected [[");
      sb.append(expected);
      sb.append("]]");
    }
    sb.append(System.lineSeparator());
  }

  public static void onTestStarted() {
    var factory = Logger.getFactory();
    if (factory instanceof TestLoggerFactory) {
      ((TestLoggerFactory)factory).clearLogBuffer();  // clear buffer from tests which failed to report their termination properly
    }
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

  /**
   * @param testName used for the log file name
   */
  public static void onTestFinished(boolean success, @NotNull String testName) {
    var factory = Logger.getFactory();
    if (factory instanceof TestLoggerFactory) {
      ((TestLoggerFactory)factory).dumpLogBuffer(success, testName);
    }
  }

  public static void logTestFailure(@NotNull Throwable t) {
    var factory = Logger.getFactory();
    if (factory instanceof TestLoggerFactory) {
      String comparisonFailures = dumpComparisonFailures(t);
      String message = comparisonFailures != null ? "test failed: " + comparisonFailures : "Test failed";

      ((TestLoggerFactory)factory).buffer(LogLevel.ERROR, "#TestFramework", message, t);
    }
  }

  private void clearLogBuffer() {
    synchronized (myBuffer) {
      myBuffer.setLength(0);
    }
  }

  private void dumpLogBuffer(boolean success, String testName) {
    String buffer;
    synchronized (myBuffer) {
      buffer = success || myBuffer.length() == 0 ? null : myBuffer.toString();
      myBuffer.setLength(0);
    }

    if (buffer != null) {
      if (mySplitTestLogs) {
        var logDir = getTestLogDir().resolve(SPLIT_LOGS_SUBDIR);
        var logFile = FileUtil.findSequentNonexistentFile(logDir.toFile(), FileUtil.sanitizeFileName(testName), "log").toPath();
        try {
          Files.createDirectories(logDir);
          Files.writeString(logFile, buffer);
          buffer = "Log saved to: " + logFile.getFileName() + " (" + logFile + ')';
        }
        catch (IOException e) {
          buffer += "\nError writing split log, disabling splitting: " + logFile + '\n' + e;
          mySplitTestLogs = false;
        }
      }

      if (System.getenv("TEAMCITY_VERSION") != null) {
        // printing in several small statements to avoid service messages tearing, causing this fold to expand
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
        var lines = LineTokenizer.tokenizeIntoList(buffer, false, false);
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

  static final class TestLoggerAssertionError extends AssertionError {
    private TestLoggerAssertionError(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final class TestLogger extends JulLogger {
    private final TestLoggerFactory myFactory;

    private TestLogger(String category, TestLoggerFactory factory) {
      super(java.util.logging.Logger.getLogger(category));
      myFactory = factory;
    }

    @Override
    public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
      var actions = LoggedErrorProcessor.getInstance().processError(myLogger.getName(), requireNonNullElse(message, ""), details, t);

      if (actions.contains(LoggedErrorProcessor.Action.LOG)) {
        if (t instanceof TestLoggerAssertionError && message.equals(t.getMessage()) && details.length == 0) {
          throw (TestLoggerAssertionError)t;
        }

        message = message + DefaultLogger.detailsToString(details) + DefaultLogger.attachmentsToString(t);
        t = ensureNotControlFlow(t);

        if (myFactory.mySplitTestLogs) {
          myFactory.buffer(LogLevel.ERROR, myLogger.getName(), message, t);
        }

        super.info(message, t);
      }

      if (actions.contains(LoggedErrorProcessor.Action.STDERR)) {
        DefaultLogger.dumpExceptionsToStderr(message, t);
      }

      if (actions.contains(LoggedErrorProcessor.Action.RETHROW)) {
        throw new TestLoggerAssertionError(message, t);
      }
    }

    @Override
    public void warn(String message, @Nullable Throwable t) {
      if (LoggedErrorProcessor.getInstance().processWarn(myLogger.getName(), requireNonNullElse(message, ""), t)) {
        message += DefaultLogger.attachmentsToString(t);
        t = ensureNotControlFlow(t);

        if (myFactory.mySplitTestLogs) {
          myFactory.buffer(LogLevel.WARNING, myLogger.getName(), message, t);
        }

        super.warn(message, t);
      }
    }

    @Override
    public void info(String message) {
      info(message, null);
    }

    @Override
    public void info(String message, @Nullable Throwable t) {
      super.info(message, t);
      myFactory.buffer(LogLevel.INFO, myLogger.getName(), message, t);
    }

    @Override
    public void debug(String message) {
      debug(message, (Throwable)null);
    }

    @Override
    public void debug(@Nullable Throwable t) {
      debug(null, t);
    }

    @Override
    public void debug(String message, @Nullable Throwable t) {
      if (isDebugEnabled()) {
        super.debug(message, t);
        myFactory.buffer(LogLevel.DEBUG, myLogger.getName(), message, t);
      }
    }

    @Override
    public void trace(String message) {
      if (isTraceEnabled()) {
        super.trace(message);
        myFactory.buffer(LogLevel.TRACE, myLogger.getName(), message, null);
      }
    }

    @Override
    public void trace(@Nullable Throwable t) {
      if (isTraceEnabled()) {
        super.trace(t);
        myFactory.buffer(LogLevel.TRACE, myLogger.getName(), null, t);
      }
    }

    @Override
    public boolean isDebugEnabled() {
      return !Accessor.isInStressTest() || super.isDebugEnabled();
    }

    /**
     * Calling {@link com.intellij.openapi.application.ex.ApplicationManagerEx#isInStressTest} reflectively to avoid dependency on a platform module
     */
    private static class Accessor {
      private static final @Nullable MethodHandle isInStressTest = getMethodHandle();

      private static MethodHandle getMethodHandle() {
        try {
          var clazz = Class.forName("com.intellij.openapi.application.ex.ApplicationManagerEx");
          return MethodHandles.publicLookup().findStatic(clazz, "isInStressTest", MethodType.methodType(boolean.class));
        }
        catch (ReflectiveOperationException e) {
          e.printStackTrace();
          return null;
        }
      }

      private static boolean isInStressTest() {
        try {
          return isInStressTest != null && (boolean)isInStressTest.invokeExact();
        }
        catch (Throwable ignored) {
          return false;
        }
      }
    }
  }
}
