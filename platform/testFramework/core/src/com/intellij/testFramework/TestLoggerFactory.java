// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AssumptionViolatedException;
import org.junit.ComparisonFailure;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;
import java.util.stream.Stream;

import static com.intellij.openapi.application.PathManager.PROPERTY_LOG_PATH;
import static com.intellij.testFramework.TestLoggerKt.recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures;
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
  private static final boolean myEchoDebugToStdout = Boolean.getBoolean("idea.test.logs.echo.debug.to.stdout");

  private static final AtomicInteger myRethrowErrorsNumber = new AtomicInteger(0);

  private final AtomicReference<DebugArtifactPublisher> myDebugArtifactPublisher = new AtomicReference<>();

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

    java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(category);
    if (myEchoDebugToStdout) {
      configureLogToStdoutIfDebug(julLogger);
    }
    return new TestLogger(julLogger, this);
  }

  /**
   * If the logger has the "FINE" level, add a LogToStdoutJulHandler that streams its log records
   * to STDOUT with a timestamp relative to the test start time.
   */
  private static void configureLogToStdoutIfDebug(@NotNull java.util.logging.Logger julLogger) {
    if (julLogger.isLoggable(Level.FINE) &&
        ContainerUtil.findInstance(julLogger.getHandlers(), LogToStdoutJulHandler.class) == null) {
      julLogger.addHandler(new LogToStdoutJulHandler());
    }
  }

  public static int getRethrowErrorNumber() {
    return myRethrowErrorsNumber.get();
  }

  public static boolean reconfigure() {
    try {
      String customConfigPath = System.getProperty(PathManager.PROPERTY_LOG_CONFIG_FILE);
      Path logProperties = customConfigPath != null ? Path.of(customConfigPath)
                                                    : Path.of(PathManager.getHomePath(), "test-log.properties");
      if (Files.exists(logProperties)) {
        if (customConfigPath != null) System.out.println("Configuring j.u.l.LogManager from file: " + logProperties);
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(logProperties))) {
          LogManager.getLogManager().readConfiguration(in);
        }
      }
      else {
        System.err.println("Configuration file for j.u.l.LogManager does not exist: " + logProperties);
      }

      Path logDir = getTestLogDir();
      Files.createDirectories(logDir);

      Path logFile = logDir.resolve(LOG_FILE_NAME);
      JulLogger.clearHandlers();
      JulLogger.configureLogFileAndConsole(logFile, false, true, false, null, null, null);

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
    String property = System.getProperty(PROPERTY_LOG_PATH);
    return property == null ? Path.of(PathManager.getSystemPath(), LOG_DIR) : Path.of(property).normalize();
  }

  public static void dumpLogToStdout(@NotNull String testStartMarker) {
    dumpLogTo(testStartMarker, System.out);
  }

  public static void dumpLogTo(@NotNull String testStartMarker, PrintStream out) {
    Path logFile = getTestLogDir().resolve(LOG_FILE_NAME);
    if (Files.exists(logFile)) {
      try {
        String logText;

        long length = Files.size(logFile);
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

        out.println("\n\nIdea Log at " + logFile + ":");
        int startPos = logText.lastIndexOf(testStartMarker);
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

  private static void enableDebugLogging(@NotNull Disposable parentDisposable, @NotNull Stream<String> categories) {
    categories.map(Logger::getInstance).filter(logger -> !logger.isDebugEnabled()).forEach(logger -> {
      logger.setLevel(LogLevel.DEBUG);
      Disposer.register(parentDisposable, () -> logger.setLevel(LogLevel.INFO));
    });
  }

  public static void enableTraceLogging(@NotNull Disposable parentDisposable, Class<?> @NotNull ... classes) {
    for (Class<?> klass : classes) {
      Logger logger = Logger.getInstance('#' + klass.getName());
      if (!logger.isTraceEnabled()) {
        logger.setLevel(LogLevel.TRACE);
        Disposer.register(parentDisposable, () -> logger.setLevel(LogLevel.INFO));
      }
    }
  }

  private void buffer(@NotNull LogLevel level, @NotNull String category, @Nullable String message, @Nullable Throwable t) {
    String source = category.substring(Math.max(category.length() - 30, 0));
    String format = String.format("%1$tH:%1$tM:%1$tS,%1$tL %2$-6s %3$30s - ", System.currentTimeMillis(), level.getLevelName(), source);
    synchronized (myBuffer) {
      myBuffer.append(format);
      if (message != null) {
        myBuffer.append(message);
      }
      myBuffer.append(System.lineSeparator());
      if (t != null) {
        StringWriter writer = new StringWriter(4096);
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

    StringBuilder sb = new StringBuilder();
    ExceptionUtil.findCauseAndSuppressed(t, ComparisonFailure.class).forEach(e ->
      logComparisonFailure(sb, e.getExpected(), e.getActual())
    );

    ExceptionUtil.findCauseAndSuppressed(t, junit.framework.ComparisonFailure.class).forEach(e ->
      logComparisonFailure(sb, e.getExpected(), e.getActual())
    );

    return sb.isEmpty() ? null : sb.toString();
  }

  private static void logComparisonFailure(@NotNull StringBuilder sb, @Nullable String expected, @Nullable String actual) {
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
    myRethrowErrorsNumber.set(0);
    TestLoggerFactory factory = getTestLoggerFactory();
    if (factory != null) {
      factory.clearLogBuffer();  // clear buffer from tests which failed to report their termination properly
      DebugArtifactPublisher publisher = factory.myDebugArtifactPublisher.getAndSet(null);
      if (publisher != null) {
        publisher.cleanup();
      }
      factory.myTestStartedMillis = System.currentTimeMillis();
    }
  }

  /** @see #onTestFinished(boolean, String) */
  public static void onTestFinished(boolean success, @NotNull Description description) {
    onTestFinished(success, description.getDisplayName());
  }

  /**
   * @param testName used for the log file name
   */
  public static void onTestFinished(boolean success, @NotNull String testName) {
    TestLoggerFactory factory = getTestLoggerFactory();
    if (factory != null) {
      factory.myTestStartedMillis = 0;
      DebugArtifactPublisher publisher = factory.myDebugArtifactPublisher.getAndSet(null);
      if (publisher != null) {
        if (!success) {
          publisher.publishArtifacts(testName);
        }
        else {
          publisher.cleanup();
        }
      }
      factory.dumpLogBuffer(success, testName);
    }
  }

  public static void logTestFailure(@Nullable Throwable t) {
    TestLoggerFactory factory = getTestLoggerFactory();
    if (factory != null) {
      String comparisonFailures = dumpComparisonFailures(t);
      String message = comparisonFailures != null ? "test failed: " + comparisonFailures : "Test failed";
      factory.buffer(LogLevel.ERROR, "#TestFramework", message, t);
    }
  }

  /**
   * Publishes {@code artifactPath} as a build artifact if the current test fails on TeamCity under 'debug-artifacts' directory.
   * @param artifactPath path to a file or directory to be published
   * @param artifactName meaningful name under which the artifact will be published; the name of the current test will be added as a prefix
   *                     automatically; and if multiple artifacts with the same {@code artifactName} are added during execution of the test,
   *                     unique suffix will also be added automatically.
   */
  public static void publishArtifactIfTestFails(@NotNull Path artifactPath, @NotNull String artifactName) {
    TestLoggerFactory factory = getTestLoggerFactory();
    if (factory != null) {
      factory.getOrCreateDebugArtifactPublisher().storeArtifact(artifactPath, artifactName);
    }
  }

  private DebugArtifactPublisher getOrCreateDebugArtifactPublisher() {
    DebugArtifactPublisher publisher = myDebugArtifactPublisher.get();
    if (publisher != null) return publisher;
    Path storagePath = PathManager.getLogDir().resolve("debug-artifacts");
    myDebugArtifactPublisher.compareAndSet(null, new DebugArtifactPublisher(storagePath));
    return myDebugArtifactPublisher.get();
  }

  private void clearLogBuffer() {
    synchronized (myBuffer) {
      myBuffer.setLength(0);
    }
  }

  private void dumpLogBuffer(boolean success, @NotNull String testName) {
    String buffer;
    synchronized (myBuffer) {
      buffer = success || myBuffer.isEmpty() ? null : myBuffer.toString();
      myBuffer.setLength(0);
    }

    if (buffer != null) {
      if (mySplitTestLogs) {
        Path logDir = getTestLogDir().resolve(SPLIT_LOGS_SUBDIR);
        Path logFile = FileUtil.findSequentNonexistentFile(logDir.toFile(), FileUtil.sanitizeFileName(testName), "log").toPath();
        try {
          Files.createDirectories(logDir);
          Files.writeString(logFile, buffer);
          String headerFooter = StringUtil.repeat("=", 80);
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
        String finalBuffer = buffer;
        TeamCityLogger.block("DEBUG log", () ->
          System.out.println(finalBuffer)
        );
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
    return RuleChain.emptyRuleChain()
      .around(new TestWatcher() {
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
      })
      .around((base, description) -> new Statement() {
        @Override
        public void evaluate() {
          recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures(() -> base.evaluate());
        }
      });
  }

  @Internal
  public static final class TestLoggerAssertionError extends AssertionError {
    TestLoggerAssertionError(String message, Throwable cause) {
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
      Set<LoggedErrorProcessor.Action>
        actions = LoggedErrorProcessor.getInstance().processError(getLoggerName(), requireNonNullElse(message, ""), details, t);

      ErrorLog errorLog = TestLoggerKt.getErrorLog();
      if (actions.contains(LoggedErrorProcessor.Action.RETHROW) && errorLog != null) {
        errorLog.recordLoggedError(message, details, t);
        return;
      }
      if (actions.contains(LoggedErrorProcessor.Action.LOG)) {
        if (t instanceof TestLoggerAssertionError && message.equals(t.getMessage()) && details.length == 0) {
          throw (TestLoggerAssertionError)t;
        }

        message = message + DefaultLogger.detailsToString(details) + DefaultLogger.attachmentsToString(t);
        t = ensureNotControlFlow(t);

        if (myFactory.mySplitTestLogs) {
          myFactory.buffer(LogLevel.ERROR, getLoggerName(), message, t);
        }

        super.info(message, t);
      }

      if (actions.contains(LoggedErrorProcessor.Action.STDERR) && DefaultLogger.shouldDumpExceptionToStderr()) {
        System.err.println("ERROR: " + message);
        if (t != null) t.printStackTrace(System.err);
      }

      if (actions.contains(LoggedErrorProcessor.Action.RETHROW)) {
        myRethrowErrorsNumber.incrementAndGet();
        throw new TestLoggerAssertionError(message, t);
      }
    }

    @Override
    public void warn(String message, @Nullable Throwable t) {
      if (LoggedErrorProcessor.getInstance().processWarn(getLoggerName(), requireNonNullElse(message, ""), t)) {
        message += DefaultLogger.attachmentsToString(t);
        t = ensureNotControlFlow(t);

        if (myFactory.mySplitTestLogs) {
          myFactory.buffer(LogLevel.WARNING, getLoggerName(), message, t);
        }

        super.warn(message, t);
      }
    }

    @Override
    public void info(String message, @Nullable Throwable t) {
      super.info(message, t);
      myFactory.buffer(LogLevel.INFO, getLoggerName(), message, t);
    }

    @Override
    public void debug(String message, @Nullable Throwable t) {
      if (isDebugEnabled()) {
        super.debug(message, t);
        myFactory.buffer(LogLevel.DEBUG, getLoggerName(), message, t);
      }
    }

    @Override
    public void trace(String message) {
      if (isTraceEnabled()) {
        super.trace(message);
        myFactory.buffer(LogLevel.TRACE, getLoggerName(), message, null);
      }
    }

    @Override
    public void trace(@Nullable Throwable t) {
      if (isTraceEnabled()) {
        super.trace(t);
        myFactory.buffer(LogLevel.TRACE, getLoggerName(), null, t);
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
      @NotNull
      private static final MethodHandle isInStressTest = getMethodHandle();

      private static @NotNull MethodHandle getMethodHandle() {
        try {
          Class<?> clazz = Class.forName("com.intellij.openapi.application.ex.ApplicationManagerEx");
          MethodHandle handle = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup()).findStatic(clazz, "isInStressTest", MethodType.methodType(boolean.class));
          Object result = handle.invokeWithArguments();
          assert result instanceof Boolean;
          return handle;
        }
        catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }

      private static boolean isInStressTest() {
        try {
          return (boolean)isInStressTest.invokeWithArguments();
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
      super(true);
    }

    @Override
    protected long getStartedMillis() {
      TestLoggerFactory factory = getTestLoggerFactory();
      return factory == null ? 0L : factory.myTestStartedMillis;
    }
  }
}
