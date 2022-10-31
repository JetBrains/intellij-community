// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
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
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;
import java.util.stream.Stream;

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
  private static final int MAX_BUFFER_LENGTH = Integer.getInteger("idea.single.test.log.max.length", 10_000_000);

  private final StringBuilder myBuffer = new StringBuilder();
  private long myTestStartedMillis;
  private boolean myInitialized;

  /** When enabled, logs produced during a failed test are saved to a separate file instead of being dumped to the stdout. */
  private boolean mySplitTestLogs = Boolean.getBoolean("idea.split.test.logs");

  /** When enabled, log records with at least "FINE" level are echoed to the stdout with a timestamp relative to the test start time. */
  private final boolean myEchoDebugToStdout = Boolean.getBoolean("idea.test.logs.echo.debug.to.stdout");

  private TestLoggerFactory() { }

  private static @Nullable TestLoggerFactory getTestLoggerFactory() {
    Logger.Factory factory = Logger.getFactory();
    return factory instanceof TestLoggerFactory ? (TestLoggerFactory)factory : null;
  }

  @Override
  public synchronized @NotNull Logger getLoggerInstance(@NotNull String category) {
    if (!myInitialized && reconfigure()) {
      myInitialized = true;
    }

    var julLogger = java.util.logging.Logger.getLogger(category);
    if (myEchoDebugToStdout) {
      configureLogToStdoutIfDebug(julLogger);
    }
    return new TestLogger(julLogger, this);
  }

  /**
   * If the logger has the "FINE" level, add a LogToStdoutJulHandler that streams its log records
   * to STDOUT with a timestamp relative to the test start time.
   */
  private static void configureLogToStdoutIfDebug(java.util.logging.Logger julLogger) {
    if (julLogger.isLoggable(Level.FINE) &&
        ContainerUtil.findInstance(julLogger.getHandlers(), LogToStdoutJulHandler.class) == null) {
      julLogger.addHandler(new LogToStdoutJulHandler());
    }
  }

  public static boolean reconfigure() {
    try {
      var customConfigPath = System.getProperty(PathManager.PROPERTY_LOG_CONFIG_FILE);
      var logProperties = customConfigPath != null ? Path.of(customConfigPath)
                                                   : Path.of(PathManager.getHomePath(), "test-log.properties");
      if (Files.exists(logProperties)) {
        if (customConfigPath != null) System.out.println("Configuring j.u.l.LogManager from file: " + logProperties);
        try (var in = new BufferedInputStream(Files.newInputStream(logProperties))) {
          LogManager.getLogManager().readConfiguration(in);
        }
      }
      else {
        System.err.println("Configuration file for j.u.l.LogManager does not exist: " + logProperties);
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
    dumpLogTo(testStartMarker, System.out);
  }

  public static void dumpLogTo(@NotNull String testStartMarker, PrintStream out) {
    var logFile = getTestLogDir().resolve(LOG_FILE_NAME);
    if (Files.exists(logFile)) {
      try {
        String logText;

        var length = Files.size(logFile);
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

        out.println("\n\nIdea Log:");
        var startPos = logText.lastIndexOf(testStartMarker);
        out.println(startPos > 0 ? logText.substring(startPos) : logText);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void enableDebugLogging(@NotNull Disposable parentDisposable, Class<?> @NotNull ... classes) {
    enableDebugLogging(parentDisposable, Stream.of(classes).map(klass -> '#' + klass.getName()));
  }

  public static void enableDebugLogging(@NotNull Disposable parentDisposable, String @NotNull ... categories) {
    enableDebugLogging(parentDisposable, Stream.of(categories));
  }

  private static void enableDebugLogging(Disposable parentDisposable, Stream<String> categories) {
    categories.map(Logger::getInstance).filter(logger -> !logger.isDebugEnabled()).forEach(logger -> {
      logger.setLevel(LogLevel.DEBUG);
      Disposer.register(parentDisposable, () -> logger.setLevel(LogLevel.INFO));
    });
  }

  public static void enableTraceLogging(@NotNull Disposable parentDisposable, Class<?> @NotNull ... classes) {
    for (var klass : classes) {
      var logger = Logger.getInstance('#' + klass.getName());
      if (!logger.isTraceEnabled()) {
        logger.setLevel(LogLevel.TRACE);
        Disposer.register(parentDisposable, () -> logger.setLevel(LogLevel.INFO));
      }
    }
  }

  private void buffer(LogLevel level, String category, @Nullable String message, @Nullable Throwable t) {
    var source = category.substring(Math.max(category.length() - 30, 0));
    var format = String.format("%1$tH:%1$tM:%1$tS,%1$tL %2$-6s %3$30s - ", System.currentTimeMillis(), level.getLevelName(), source);
    synchronized (myBuffer) {
      myBuffer.append(format);
      if (message != null) {
        myBuffer.append(message);
      }
      myBuffer.append(System.lineSeparator());
      if (t != null) {
        var writer = new StringWriter(4096);
        t.printStackTrace(new PrintWriter(writer));
        myBuffer.append(writer.getBuffer());
        myBuffer.append(System.lineSeparator());
      }
      if (myBuffer.length() > MAX_BUFFER_LENGTH) {
        myBuffer.delete(0, myBuffer.length() - MAX_BUFFER_LENGTH + MAX_BUFFER_LENGTH / 4);
      }
    }
  }

  /**
   * Report full contents, not limited to 20 characters of ComparisonFailure#MAX_CONTEXT_LENGTH
   */
  private static @Nullable String dumpComparisonFailures(@Nullable Throwable t) {
    if (t == null) return null;

    var sb = new StringBuilder();
    ExceptionUtil.findCauseAndSuppressed(t, ComparisonFailure.class).forEach(e -> {
      logComparisonFailure(sb, e.getExpected(), e.getActual());
    });

    ExceptionUtil.findCauseAndSuppressed(t, junit.framework.ComparisonFailure.class).forEach(e -> {
      logComparisonFailure(sb, e.getExpected(), e.getActual());
    });

    return sb.length() != 0 ? sb.toString() : null;
  }

  private static void logComparisonFailure(StringBuilder sb, @Nullable String expected, @Nullable String actual) {
    if (expected == null && actual == null) return;

    sb.append("Comparison Failure").append(System.lineSeparator());

    if (actual == null) {
      sb.append("Actual [null]");
    }
    else {
      sb.append("Actual [[").append(actual).append("]]");
    }
    sb.append(System.lineSeparator());

    if (expected == null) {
      sb.append("Expected [null]");
    }
    else {
      sb.append("Expected [[").append(expected).append("]]");
    }
    sb.append(System.lineSeparator());
  }

  public static void onTestStarted() {
    var factory = getTestLoggerFactory();
    if (factory != null) {
      factory.clearLogBuffer();  // clear buffer from tests which failed to report their termination properly
      factory.myTestStartedMillis = System.currentTimeMillis();
    }
  }

  /** @deprecated use {@link #onTestFinished(boolean, Description)} or {@link #onTestFinished(boolean, String)} instead */
  @Deprecated
  public static void onTestFinished(boolean success) {
    onTestFinished(success, "unnamed_test");
  }

  /** @see #onTestFinished(boolean, String) */
  public static void onTestFinished(boolean success, @NotNull Description description) {
    onTestFinished(success, description.getDisplayName());
  }

  /**
   * @param testName used for the log file name
   */
  public static void onTestFinished(boolean success, @NotNull String testName) {
    var factory = getTestLoggerFactory();
    if (factory != null) {
      factory.myTestStartedMillis = 0;
      factory.dumpLogBuffer(success, testName);
    }
  }

  public static void logTestFailure(@NotNull Throwable t) {
    var factory = getTestLoggerFactory();
    if (factory != null) {
      var comparisonFailures = dumpComparisonFailures(t);
      var message = comparisonFailures != null ? "test failed: " + comparisonFailures : "Test failed";
      factory.buffer(LogLevel.ERROR, "#TestFramework", message, t);
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
          var headerFooter = StringUtil.repeat("=", 80);
          buffer = "\n" + headerFooter +
                   "\nLog saved to: " + logFile.getFileName() +
                   "\n    (" + logFile + ")" +
                   "\n" + headerFooter +
                   "\n";
        }
        catch (IOException e) {
          buffer += "\nError writing split log, disabling splitting: " + logFile + '\n' + e;
          mySplitTestLogs = false;
        }
      }

      if (System.getenv("TEAMCITY_VERSION") != null) {
        var finalBuffer = buffer;
        TeamCityLogger.block("DEBUG log", () -> {
          System.out.println(finalBuffer);
        });
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

    private TestLogger(java.util.logging.Logger julLogger, TestLoggerFactory factory) {
      super(julLogger);
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

  // Cannot extend from ConsoleHandler since it is hard-coded to System.err,
  // and calling setOutputStream(System.out) after the constructor would close System.err.
  private static class LogToStdoutJulHandler extends StreamHandler {
    private boolean initialized;

    LogToStdoutJulHandler() {
      super(System.out, new WithTimeSinceTestStartedJulFormatter());
      setLevel(Level.ALL);
    }

    @Override
    public synchronized void publish(LogRecord record) {
      super.publish(record);
      // Flushing after every published record is useful for getting immediate feedback during debugging.
      // It is not time-critical since typically only warnings and errors reach this console handler.
      flush();
    }

    @Override
    protected synchronized void setOutputStream(OutputStream out) throws SecurityException {
      // This method is called once from the constructor.
      // Any later call would close the current output stream, in this case System.out.
      // Prevent that.
      // See https://stackoverflow.com/a/51642136.
      if (initialized) throw new UnsupportedOperationException("TestLoggerFactory.setOutputStream");
      super.setOutputStream(out);
      initialized = true;
    }

    @Override
    public synchronized void close() {
      // Prevent closing System.out.
      flush();
    }
  }

  private static class WithTimeSinceTestStartedJulFormatter extends IdeaLogRecordFormatter {
    WithTimeSinceTestStartedJulFormatter() {
      super(false);
    }

    @Override
    protected long getStartedMillis() {
      var factory = getTestLoggerFactory();
      return factory == null ? 0L : factory.myTestStartedMillis;
    }
  }
}
