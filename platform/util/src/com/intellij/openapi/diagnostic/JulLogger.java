// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.logging.*;

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

  protected final @NotNull String getLoggerName() {
    return myLogger.getName();
  }

  protected final void logSevere(@NotNull String msg) {
    logSevere(msg, null);
  }

  protected final void logSevere(@NotNull String msg, @Nullable Throwable t) {
    log(new LogEvent(myLogger, LogLevel.ERROR, msg, t));
  }

  @Override
  public boolean isTraceEnabled() {
    return myLogger.isLoggable(Level.FINER);
  }

  @Override
  public void trace(String message) {
    log(new LogEvent(myLogger, LogLevel.TRACE, message, null));
  }

  @Override
  public void trace(@Nullable Throwable t) {
    log(new LogEvent(myLogger, LogLevel.TRACE, "", t));
  }

  @Override
  public boolean isDebugEnabled() {
    return myLogger.isLoggable(Level.FINE);
  }

  @Override
  public void debug(String message, @Nullable Throwable t) {
    log(new LogEvent(myLogger, LogLevel.DEBUG, message, t));
  }

  @Override
  public void info(String message, @Nullable Throwable t) {
    log(new LogEvent(myLogger, LogLevel.INFO, message, t));
  }

  @Override
  public void warn(String message, @Nullable Throwable t) {
    log(new LogEvent(myLogger, LogLevel.WARNING, message, t));
  }

  @Override
  public void error(String message, @Nullable Throwable t, String @NotNull ... details) {
    String fullMessage = details.length > 0 ? message + "\nDetails: " + String.join("\n", details) : message;
    log(new LogEvent(myLogger, LogLevel.ERROR, fullMessage, t));
  }

  @Override
  public void setLevel(@NotNull LogLevel level) {
    myLogger.setLevel(level.getLevel());
  }

  public static void clearHandlers() {
    clearHandlers(java.util.logging.Logger.getLogger(""));
  }

  public static void clearHandlers(java.util.logging.Logger logger) {
    for (Handler handler : logger.getHandlers()) {
      logger.removeHandler(handler);
    }
  }

  @ApiStatus.Internal
  public static void configureLogFileAndConsole(
    @NotNull Path logFilePath,
    boolean appendToFile,
    boolean enableConsoleLogger,
    boolean showDateInConsole,
    @Nullable Runnable onRotate,
    @Nullable Filter filter,
    @Nullable Path inMemoryLogPath
  ) {
    java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
    IdeaLogRecordFormatter layout = new IdeaLogRecordFormatter();

    rootLogger.addHandler(configureFileHandler(logFilePath, appendToFile, onRotate, LOG_FILE_SIZE_LIMIT, LOG_FILE_COUNT, layout, filter));

    if (enableConsoleLogger) {
      rootLogger.addHandler(configureConsoleHandler(showDateInConsole, layout, filter));
    }

    if (inMemoryLogPath != null) {
      rootLogger.addHandler(configureInMemoryHandler(inMemoryLogPath));
    }
  }

  private static Handler configureConsoleHandler(boolean showDateInConsole, IdeaLogRecordFormatter layout, @Nullable Filter filter) {
    OptimizedConsoleHandler consoleHandler = new OptimizedConsoleHandler();
    consoleHandler.setFormatter(new IdeaLogRecordFormatter(showDateInConsole, layout));
    consoleHandler.setLevel(Level.WARNING);
    if (filter != null) {
      consoleHandler.setFilter(filter);
    }
    return consoleHandler;
  }

  private static InMemoryHandler configureInMemoryHandler(Path logFilePath) {
    InMemoryHandler inMemoryHandler = new InMemoryHandler(logFilePath);
    inMemoryHandler.setFormatter(new IdeaLogRecordFormatter());
    inMemoryHandler.setLevel(Level.FINEST);
    return inMemoryHandler;
  }

  @SuppressWarnings("SameParameterValue")
  private static RollingFileHandler configureFileHandler(
    @NotNull Path logFilePath,
    boolean appendToFile,
    @Nullable Runnable onRotate,
    long limit,
    int count,
    IdeaLogRecordFormatter layout,
    @Nullable Filter filter
  ) {
    RollingFileHandler fileHandler = new RollingFileHandler(logFilePath, limit, count, appendToFile, onRotate);
    fileHandler.setFormatter(layout);
    fileHandler.setLevel(Level.FINEST);
    if (filter != null) {
      fileHandler.setFilter(filter);
    }
    return fileHandler;
  }

  public static Filter createFilter(@NotNull List<String> classesToFilter) {
    return record -> {
      String loggerName = record.getLoggerName();
      boolean isFiltered = ContainerUtil.exists(classesToFilter, loggerName::startsWith);
      return !isFiltered || record.getLevel().intValue() > Level.FINE.intValue();
    };
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
