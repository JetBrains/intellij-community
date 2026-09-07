// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.ShutDownTracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static com.intellij.openapi.diagnostic.AsyncLogKt.log;
import static com.intellij.openapi.diagnostic.AsyncLogKt.shutdownLogProcessing;

@ApiStatus.Internal
public class JulLogger extends Logger {
  public static final long LOG_FILE_SIZE_LIMIT = Long.getLong("idea.log.limit", 10_000_000);
  public static final int LOG_FILE_COUNT = Integer.getInteger("idea.log.count", 12);

  private static final boolean CLEANER_DELAYED;
  static {
    boolean delayed = false;
    try {
      delayed = delayCleanerUntilIdeShutdownActivitiesFinished();
    }
    catch (Exception e) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println("Be careful, logger will be shut down earlier than application: " + e.getMessage());
    }
    CLEANER_DELAYED = delayed;
  }

  @SuppressWarnings("NonConstantLogger")
  private final java.util.logging.Logger myLogger;

  public JulLogger(java.util.logging.Logger delegate) {
    myLogger = delegate;
  }

  @VisibleForTesting
  public final @NotNull String getLoggerName() {
    return myLogger.getName();
  }

  protected final void logSevere(@NotNull String msg) {
    logSevere(msg, null, UnhandledExceptionKind.HANDLED);
  }

  /**
   * Writes {@code t} as the real cause, and carries {@code unhandledExceptionKind} beside it.
   * A caller that already split an {@link UnhandledException} passes both parts here.
   * See {@link IdeaLogRecord} and IJPL-254578.
   */
  protected final void logSevere(@NotNull String msg, @Nullable Throwable t, @NotNull UnhandledExceptionKind unhandledExceptionKind) {
    if (myLogger.isLoggable(LogLevel.ERROR.getLevel())) {
      log(event(LogLevel.ERROR, msg, t, unhandledExceptionKind));
    }
  }

  private @NotNull LogEvent event(@NotNull LogLevel level, @Nullable String message, @Nullable Throwable t) {
    return event(level, message, t, UnhandledExceptionKind.HANDLED);
  }

  /**
   * The one place where a throwable enters a {@link LogRecord}.
   * It drops an {@link UnhandledException} wrapper, so {@code LogRecord.getThrown()} always holds the real cause.
   * The kind travels apart, in an {@link IdeaLogRecord}. A log handler needs no knowledge of the wrapper.
   * See IJPL-254578.
   */
  private @NotNull LogEvent event(@NotNull LogLevel level,
                                  @Nullable String message,
                                  @Nullable Throwable t,
                                  @NotNull UnhandledExceptionKind unhandledExceptionKind) {
    if (!(t instanceof UnhandledException)) {
      return new LogEvent(myLogger, level, message, t, unhandledExceptionKind);
    }
    UnhandledException.ExceptionAndWrapper unwrapped = UnhandledException.unwrapIfUnhandled(t);
    // A caller that split the wrapper knows the kind. A caller that gave the wrapper does not, so read it here.
    UnhandledExceptionKind kind = unhandledExceptionKind == UnhandledExceptionKind.HANDLED
                                  ? unwrapped.getUnhandledExceptionKind()
                                  : unhandledExceptionKind;
    return new LogEvent(myLogger, level, message, unwrapped.getRealCause(), kind);
  }

  @Override
  public boolean isTraceEnabled() {
    return myLogger.isLoggable(Level.FINER);
  }

  @Override
  public void trace(String message) {
    if (myLogger.isLoggable(LogLevel.TRACE.getLevel())) {
      log(event(LogLevel.TRACE, message, null));
    }
  }

  @Override
  public void trace(@Nullable Throwable t) {
    if (myLogger.isLoggable(LogLevel.TRACE.getLevel())) {
      log(event(LogLevel.TRACE, "", t));
    }
  }

  @Override
  public boolean isDebugEnabled() {
    return myLogger.isLoggable(Level.FINE);
  }

  @Override
  public void debug(String message, @Nullable Throwable t) {
    if (myLogger.isLoggable(LogLevel.DEBUG.getLevel())) {
      log(event(LogLevel.DEBUG, message, t));
    }
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    if (myLogger.isLoggable(LogLevel.INFO.getLevel())) {
      log(event(LogLevel.INFO, message, t));
    }
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    if (myLogger.isLoggable(LogLevel.WARNING.getLevel())) {
      log(event(LogLevel.WARNING, message, t));
    }
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    if (myLogger.isLoggable(LogLevel.ERROR.getLevel())) {
      String fullMessage = details.length > 0 ? message + "\nDetails: " + String.join("\n", details) : message;
      log(event(LogLevel.ERROR, fullMessage, t));
    }
  }

  @Override
  public void setLevel(@Nullable LogLevel level) {
    myLogger.setLevel(level == null ? null : level.getLevel());
  }

  public LogLevel getLevel() {
    Level level = myLogger.getLevel();
    return level==null?null:LogLevel.from(level);
  }

  public static void clearHandlers() {
    clearHandlers(java.util.logging.Logger.getLogger(""));
  }

  public static void clearHandlers(@NotNull java.util.logging.Logger logger) {
    for (Handler handler : logger.getHandlers()) {
      logger.removeHandler(handler);
    }
  }

  /** @deprecated use {@link #configureStandardLoggers} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @SuppressWarnings("unused")
  public static void configureLogFileAndConsole(
    @NotNull Path logFilePath,
    boolean appendToFile,
    boolean enableConsoleLogger,
    boolean showDateInConsole,
    boolean writeAttachments,
    @Nullable Runnable onRotate,
    @Nullable Filter filter,
    @Nullable Path inMemoryLogPath
  ) {
    LogLevel consoleLogLevel = enableConsoleLogger ? LogLevel.WARNING : LogLevel.OFF;
    configureStandardLoggers(consoleLogLevel, showDateInConsole, logFilePath, appendToFile, writeAttachments, onRotate);
  }

  public static void configureStandardLoggers(
    @NotNull LogLevel consoleLogLevel, boolean showDateInConsole,
    @NotNull Path logFilePath, boolean appendToFile, boolean writeAttachments, @Nullable Runnable onRotate
  ) {
    java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
    IdeaLogRecordFormatter layout = new IdeaLogRecordFormatter();

    rootLogger.addHandler(configureFileHandler(logFilePath, appendToFile, onRotate, LOG_FILE_SIZE_LIMIT, LOG_FILE_COUNT, layout));

    if (writeAttachments) {
      rootLogger.addHandler(new AttachmentHandler(logFilePath));
    }

    if (consoleLogLevel != LogLevel.OFF) {
      rootLogger.addHandler(configureConsoleHandler(consoleLogLevel, showDateInConsole, layout));
    }
  }

  private static Handler configureConsoleHandler(LogLevel logLevel, boolean showDateInConsole, IdeaLogRecordFormatter layout) {
    OptimizedConsoleHandler consoleHandler = new OptimizedConsoleHandler();
    consoleHandler.setFormatter(new IdeaLogRecordFormatter(showDateInConsole, layout));
    consoleHandler.setLevel(logLevel.getLevel());
    return consoleHandler;
  }

  private static Handler configureFileHandler(
    Path logFilePath,
    boolean appendToFile,
    @Nullable Runnable onRotate,
    @SuppressWarnings("SameParameterValue") long limit,
    @SuppressWarnings("SameParameterValue") int count,
    IdeaLogRecordFormatter layout
  ) {
    RollingFileHandler fileHandler = new RollingFileHandler(logFilePath, limit, count, appendToFile, onRotate);
    fileHandler.setFormatter(layout);
    fileHandler.setLevel(Level.FINEST);
    return fileHandler;
  }

  private static final class OptimizedConsoleHandler extends ConsoleHandler {
    @Override
    public void publish(LogRecord record) {
      // checking levels _before_ calling a synchronized method
      if (isLoggable(record)) {
        super.publish(record);
      }
    }
  }

  private static boolean delayCleanerUntilIdeShutdownActivitiesFinished() throws Exception {
    Class<?> logManagerCleanerClass = Class.forName("java.util.logging.LogManager$Cleaner");
    Class<?> appShutdownHooks = Class.forName("java.lang.ApplicationShutdownHooks");
    Field hooksField = appShutdownHooks.getDeclaredField("hooks");
    hooksField.setAccessible(true);
    IdentityHashMap<?, ?> hooks = (IdentityHashMap<?, ?>) hooksField.get(null);
    synchronized (appShutdownHooks) {
      for (Object o : hooks.keySet()) {
        if (o instanceof Thread && logManagerCleanerClass.isAssignableFrom(o.getClass())) {
          Thread logCloseThread = (Thread)o;
          ShutDownTracker.getInstance().registerShutdownTask(() -> {
            shutdownLogProcessing();
            //noinspection CallToThreadRun
            logCloseThread.run();
          });
          hooks.remove(o);
          return true;
        }
      }
    }
    return false;
  }

  @TestOnly
  public static boolean isJulLoggerCleanerDelayed() {
    return CLEANER_DELAYED;
  }
}
