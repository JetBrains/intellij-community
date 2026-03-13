// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter;
import com.intellij.openapi.diagnostic.JulLogger;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
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
  private static final String LOG_DIR = "testlog";
  private static final String LOG_FILE_NAME = "idea.log";
  private static final String SPLIT_LOGS_SUBDIR = "splitTestLogs";

  private static final long LOG_SIZE_LIMIT = 100 * 1024 * 1024;
  private static final long LOG_SEEK_WINDOW = 100 * 1024;

  /**
   * used in {@link org.jetbrains.idea.devkit.run.FailedTestDebugLogConsoleFolding#shouldFoldLine}
   */
  private static final char FAILED_TEST_DEBUG_OUTPUT_MARKER = '\u2003';
  private static final int MAX_BUFFER_LENGTH = Math.max(1024, Integer.getInteger("idea.single.test.log.max.length", 10_000_000));

  private final StringBuilder myBuffer = new StringBuilder(); // guarded by myBuffer
  private long myTestStartedMillis;
  private boolean myInitialized;

  /// When enabled, logs produced during a failed test are saved to a separate file instead of being dumped to the stdout.
  private boolean mySplitTestLogs = Boolean.getBoolean("idea.split.test.logs");

  /// When enabled, log records with at least "FINE" level are echoed to the stdout with a timestamp relative to the test start time.
  private static final boolean myEchoDebugToStdout = Boolean.getBoolean("idea.test.logs.echo.debug.to.stdout");

  private static final AtomicInteger myRethrowErrorsNumber = new AtomicInteger(0);
  private static final ThreadLocal<Class<?>> ourCurrentTestClass = new ThreadLocal<>();

  private final AtomicReference<DebugArtifactPublisher> myDebugArtifactPublisher = new AtomicReference<>();

  private TestLoggerFactory() { }

  private static @Nullable TestLoggerFactory getTestLoggerFactory() {
    return Logger.getFactory() instanceof TestLoggerFactory test ? test : null;
  }

  @Override
  public synchronized @NotNull Logger getLoggerInstance(@NotNull String category) {
    if (!myInitialized && reconfigure()) {
      myInitialized = true;
    }

    return new TestLogger(java.util.logging.Logger.getLogger(category), this);
  }

  public static int getRethrowErrorNumber() {
    return myRethrowErrorsNumber.get();
  }

  public static boolean reconfigure() {
    try {
      var customConfigPath = System.getProperty(PathManager.PROPERTY_LOG_CONFIG_FILE);
      var logProperties = customConfigPath != null ? Path.of(customConfigPath) : PathManager.getHomeDir().resolve("test-log.properties");
      if (Files.exists(logProperties)) {
        if (customConfigPath != null) System.out.println("Configuring j.u.l.LogManager from file: " + logProperties);
        try (var in = new BufferedInputStream(Files.newInputStream(logProperties))) {
          LogManager.getLogManager().readConfiguration(in);
        }
      }
      else {
        System.err.println("Configuration file for j.u.l.LogManager does not exist: " + logProperties);
      }

      var logDir = Files.createDirectories(getTestLogDir());
      var logFile = logDir.resolve(LOG_FILE_NAME);
      JulLogger.clearHandlers();
      JulLogger.configureLogFileAndConsole(logFile, false, true, false, true, null, null, null);

      if (myEchoDebugToStdout) {
        addConsoleAppenderForDebugRecords();
      }

      System.out.printf("Test log file: %s%n", logFile.toUri());

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

  private static void addConsoleAppenderForDebugRecords() {
    var rootLogger = java.util.logging.Logger.getLogger("");

    // just add a single console appender instead of multiple handlers, but for the root logger
    rootLogger.addHandler(new FilteringLogToStdoutJulHandler(Level.FINE));
  }

  public static @NotNull Path getTestLogDir() {
    var property = System.getProperty(PROPERTY_LOG_PATH);
    return property != null ? Path.of(property).normalize() : PathManager.getSystemDir().resolve(LOG_DIR);
  }

  public static void dumpLogToStdout(@NotNull String testStartMarker) {
    dumpLogTo(testStartMarker, System.out);
  }

  public static void dumpLogTo(@NotNull String testStartMarker, @NotNull PrintStream out) {
    var logFile = getTestLogDir().resolve(LOG_FILE_NAME);
    if (Files.exists(logFile)) {
      try {
        String logText;

        var length = Files.size(logFile);
        if (length > LOG_SEEK_WINDOW) {
          try (@SuppressWarnings("IO_FILE_USAGE") var file = new RandomAccessFile(logFile.toFile(), "r")) {
            file.seek(length - LOG_SEEK_WINDOW);
            var bytes = new byte[(int)LOG_SEEK_WINDOW];
            var read = file.read(bytes);
            logText = new String(bytes, 0, read, StandardCharsets.UTF_8);
          }
        }
        else {
          logText = Files.readString(logFile);
        }

        out.println("\n\nIdea Log at " + logFile + ":");
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

  private static void enableDebugLogging(@NotNull Disposable parentDisposable, @NotNull Stream<String> categories) {
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

  private void buffer(@NotNull LogLevel level, @NotNull String category, @Nullable String message, @Nullable Throwable t) {
    synchronized (myBuffer) {
      formatTimeStampLevelAndSource(level, category);
      myBuffer.append(" - ");
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

  private long cachedTime;
  private String cachedFormattedTime;
  private static final DateTimeFormatter TIME_STAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss,SSS");
  // faster version of `String.format("%1$tH:%1$tM:%1$tS,%1$tL %2$-6s %3$30s", System.currentTimeMillis(), level.getLevelName(), source);`
  // uses pre-parsed time format string and caches the formatted timestamp, because it doesn't change very often,
  // uses allocation-free padLeft/padRight to avoid reparsing format string for appending category and level
  private void formatTimeStampLevelAndSource(@NotNull LogLevel level, @NotNull String category) {
    long currentTime = System.currentTimeMillis();
    String formattedTime;
    if (currentTime == cachedTime) {
      formattedTime = cachedFormattedTime;
    }
    else {
      formattedTime = TIME_STAMP_FORMAT.format(LocalTime.now());
      cachedFormattedTime = formattedTime;
      cachedTime = currentTime;
    }
    myBuffer.append(formattedTime);
    myBuffer.append(' ');
    StringUtil.padRight(myBuffer, level.getLevelName(), 6, 6);
    myBuffer.append(' ');
    StringUtil.padLeft(myBuffer, category, 30, 30);
  }

  /// Report full contents, not limited to 20 characters of ComparisonFailure#MAX\_CONTEXT\_LENGTH
  private static @Nullable String dumpComparisonFailures(@Nullable Throwable t) {
    if (t == null) return null;

    var sb = new StringBuilder();
    ExceptionUtil.causeAndSuppressed(t, ComparisonFailure.class).forEach(e ->
      logComparisonFailure(sb, e.getExpected(), e.getActual())
    );

    ExceptionUtil.causeAndSuppressed(t, junit.framework.ComparisonFailure.class).forEach(e ->
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
    onTestStarted(null);
  }

  public static void onTestStarted(@Nullable Class<?> testClass) {
    if (testClass == null) {
      ourCurrentTestClass.remove();
    }
    else {
      ourCurrentTestClass.set(testClass);
    }
    myRethrowErrorsNumber.set(0);
    var factory = getTestLoggerFactory();
    if (factory != null) {
      // clear buffer from tests which failed to report their termination properly
      synchronized (factory.myBuffer) {
        factory.myBuffer.setLength(0);
      }
      var publisher = factory.myDebugArtifactPublisher.getAndSet(null);
      if (publisher != null) {
        publisher.cleanup();
      }
      factory.myTestStartedMillis = System.currentTimeMillis();
    }
  }

  public static @Nullable Class<?> getCurrentTestClass() {
    return ourCurrentTestClass.get();
  }

  /// @see #onTestFinished(boolean, String)
  public static void onTestFinished(boolean success, @NotNull Description description) {
    onTestFinished(success, description.getDisplayName());
  }

  /// @param testName used for the log file name
  public static void onTestFinished(boolean success, @NotNull String testName) {
    ourCurrentTestClass.remove();
    var factory = getTestLoggerFactory();
    if (factory != null) {
      factory.myTestStartedMillis = 0;
      var publisher = factory.myDebugArtifactPublisher.getAndSet(null);
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

  private @NotNull CharSequence myBufferStaticFixtureInit = "";
  private @NotNull CharSequence myBufferFixtureInit = "";
  public static void fixtureInitialization(boolean isStatic, @NotNull Runnable runnable) {
    try {
      runnable.run();
    }
    finally {
      var factory = getTestLoggerFactory();
      if (factory != null) {
        synchronized (factory.myBuffer) {
          if (isStatic) {
            factory.myBuffer.append("---Static Fixtures Initialization End---");
            factory.myBuffer.append(System.lineSeparator());
            factory.myBufferStaticFixtureInit = factory.myBuffer.toString();
          }
          else {
            factory.myBuffer.append("---Instance Fixtures Initialization End---");
            factory.myBuffer.append(System.lineSeparator());
            factory.myBufferFixtureInit = factory.myBuffer.toString();
          }
          factory.myBuffer.setLength(0);
        }
      }
    }
  }

  public static void onFixturesDisposeStart(boolean isStatic) {
    var factory = getTestLoggerFactory();
    if (factory != null) {
      if (isStatic) {
        factory.myBufferStaticFixtureInit = "";
      }
      factory.myBufferFixtureInit = "";
    }
  }

  public static void logTestFailure(@Nullable Throwable t) {
    var factory = getTestLoggerFactory();
    if (factory != null) {
      var comparisonFailures = dumpComparisonFailures(t);
      var message = comparisonFailures != null ? "test failed: " + comparisonFailures : "Test failed";
      factory.buffer(LogLevel.ERROR, "#TestFramework", message, t);
    }
  }

  /// Publishes `artifactPath` as a build artifact if the current test fails on TeamCity under 'debug-artifacts' directory.
  /// @param artifactPath path to a file or directory to be published
  /// @param artifactName meaningful name under which the artifact will be published; the name of the current test will be added as a prefix
  ///                     automatically; and if multiple artifacts with the same `artifactName` are added during execution of the test,
  ///                     unique suffix will also be added automatically.
  public static void publishArtifactIfTestFails(@NotNull Path artifactPath, @NotNull String artifactName) {
    var factory = getTestLoggerFactory();
    if (factory != null) {
      factory.getOrCreateDebugArtifactPublisher().storeArtifact(artifactPath, artifactName);
    }
  }

  private DebugArtifactPublisher getOrCreateDebugArtifactPublisher() {
    var publisher = myDebugArtifactPublisher.get();
    if (publisher != null) return publisher;
    var storagePath = PathManager.getLogDir().resolve("debug-artifacts");
    myDebugArtifactPublisher.compareAndSet(null, new DebugArtifactPublisher(storagePath));
    return myDebugArtifactPublisher.get();
  }

  private void dumpLogBuffer(boolean success, @NotNull String testName) {
    String buffer;
    synchronized (myBuffer) {
      buffer = success || myBuffer.isEmpty() && myBufferStaticFixtureInit.isEmpty() && myBufferFixtureInit.isEmpty() ? null :
               myBufferStaticFixtureInit + "\n" + myBufferFixtureInit + "\n" + myBuffer;
      myBuffer.setLength(0);
    }

    if (buffer != null) {
      if (mySplitTestLogs) {
        var logFile = (Path)null;
        try {
          logFile = createNextFile(FileUtil.sanitizeFileName(testName));
          Files.writeString(logFile, buffer);
          var headerFooter = StringUtil.repeat("=", 80);
          buffer = """
            
            %s
            Log saved to: %s
            %s
            """.formatted(headerFooter, logFile, headerFooter);
        }
        catch (IOException e) {
          buffer += "\nError writing split log, disabling splitting: " + logFile + '\n' + e;
          mySplitTestLogs = false;
        }
      }

      if (System.getenv("TEAMCITY_VERSION") != null) {
        CharSequence finalBuffer = buffer;
        TeamCityLogger.block("DEBUG log", () ->
          System.out.println(finalBuffer)
        );
      }
      else {
        // mark each line in IDEA console with this hidden mark to be able to fold it automatically
        var lines = LineTokenizer.tokenizeIntoList(buffer, false, false);
        if (!lines.getFirst().startsWith("\n")) lines = ContainerUtil.prepend(lines.subList(1, lines.size()), "\n" + lines.getFirst());
        System.err.println(String.join(FAILED_TEST_DEBUG_OUTPUT_MARKER + "\n", lines));
      }
    }
  }

  private static Path createNextFile(String testName) throws IOException {
    var logDir = Files.createDirectories(getTestLogDir().resolve(SPLIT_LOGS_SUBDIR));
    for (var i = 0; ; i++) {
      try {
        return Files.createFile(logDir.resolve(testName + i + ".log"));
      }
      catch (FileAlreadyExistsException ignored) { }
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

    private TestLogger(@NotNull java.util.logging.Logger julLogger, @NotNull TestLoggerFactory factory) {
      super(julLogger);
      myFactory = factory;
    }

    @Override
    public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
      var actions = LoggedErrorProcessor.getInstance().processError(getLoggerName(), requireNonNullElse(message, ""), details, t);

      var errorLog = TestLoggerKt.getErrorLog();
      if (actions.contains(LoggedErrorProcessor.Action.RETHROW) && errorLog != null) {
        errorLog.recordLoggedError(message, t);
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
      return !isInStressTest() || super.isDebugEnabled();
    }
  }

  // Cannot extend from ConsoleHandler since it is hard-coded to System.err,
  // and calling setOutputStream(System.out) after the constructor would close System.err.
  public static class LogToStdoutJulHandler extends StreamHandler {
    private boolean initialized;

    public LogToStdoutJulHandler() {
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

  private static class FilteringLogToStdoutJulHandler extends LogToStdoutJulHandler {
    FilteringLogToStdoutJulHandler(Level level) {
      super();

      // we'd like to capture all records with level or finer than the level
      // so we set level to all and do actual level filtering with the filter
      setFilter(record -> record.getLevel().intValue() <= level.intValue());
    }
  }

  private static class WithTimeSinceTestStartedJulFormatter extends IdeaLogRecordFormatter {
    WithTimeSinceTestStartedJulFormatter() {
      super(true);
    }

    @Override
    protected long getStartedMillis() {
      var factory = getTestLoggerFactory();
      return factory == null ? 0L : factory.myTestStartedMillis;
    }
  }
}
