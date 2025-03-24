// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/**
 * Write messages to {@code idea.log}.
 * <p>
 * Production mode:
 * <ul>
 * <li>The log messages go to {@code %system%/log/idea.log}.
 * <li>Error and warning messages go to {@link System#err}. To suppress them, set {@code -Didea.log.console=false}.
 * <li>Error, warning and info messages go to the log file.
 * <li>Debug and trace messages are dropped by default.
 * <li>In EAP versions or if the {@code idea.fatal.error.notification} system property is set to {@code true},
 * errors additionally result in an 'IDE Internal Error'.
 * See {@link com.intellij.diagnostic.DialogAppender DialogAppender} for more details.
 * <li>The log level of each logger can be adjusted in
 * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>.
 * </ul>
 * <p>
 * Test mode in tests that extend {@code UsefulTestCase}:
 * <ul>
 * <li>The log messages go to {@code %system%/testlog/idea.log}.
 * <li>Error and warning messages go directly to the console.
 * <li>Error messages additionally throw an {@link AssertionError}.
 * <li>Info and debug messages are buffered in memory.
 * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
 * <li>Trace messages are dropped.
 * <li>To configure the log level during a single test,
 * see {@code TestLoggerFactory.enableDebugLogging} and {@code TestLoggerFactory.enableTraceLogging}.
 * </ul>
 */
public abstract class Logger {
  private static boolean isUnitTestMode;

  public interface Factory {
    @NotNull Logger getLoggerInstance(@NotNull String category);
  }

  private static final class DefaultFactory implements Factory {
    @Override
    public @NotNull Logger getLoggerInstance(@NotNull String category) {
      return new DefaultLogger(category);
    }
  }

  private static Factory ourFactory = new DefaultFactory();

  public static void setFactory(@NotNull Class<? extends Factory> factory) {
    if (isInitialized()) {
      if (factory.isInstance(ourFactory)) {
        return;
      }

      logFactoryChanged(factory);
    }

    try {
      Constructor<? extends Factory> constructor = factory.getDeclaredConstructor();
      constructor.setAccessible(true);
      ourFactory = constructor.newInstance();
    }
    catch (Exception e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  public static void setFactory(@NotNull Factory factory) {
    if (isInitialized()) {
      logFactoryChanged(factory.getClass());
    }

    ourFactory = factory;
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void logFactoryChanged(Class<? extends Factory> factory) {
    if (Boolean.getBoolean("idea.log.logger.factory.changed")) {
      System.out.println("Changing log factory from " + ourFactory.getClass().getCanonicalName() +
                         " to " + factory.getCanonicalName() + '\n' + ExceptionUtil.getThrowableText(new Throwable()));
    }
  }

  public static Factory getFactory() {
    return ourFactory;
  }

  public static boolean isInitialized() {
    return !(ourFactory instanceof DefaultFactory);
  }

  public static @NotNull Logger getInstance(@NotNull String category) {
    return ourFactory.getLoggerInstance(category);
  }

  public static @NotNull Logger getInstance(@NotNull Class<?> cl) {
    return ourFactory.getLoggerInstance("#" + cl.getName());
  }

  public abstract boolean isDebugEnabled();

  /**
   * Log a debug message.
   * <p>
   * In production mode, debug messages are disabled by default.
   * They can be enabled in
   * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>.
   * <p>
   * In UsefulTestCase mode, debug messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   *
   * @param message should be a plain string literal,
   *                or the call should be enclosed in {@link #isDebugEnabled()};
   *                for all other cases, {@link #debug(String, Object...)} is more efficient
   *                as it delays building the string and calling {@link Object#toString()} on the arguments
   */
  public void debug(String message) {
    debug(message, (Throwable)null);
  }

  /**
   * Log a stack trace at debug level, without any message.
   * <p>
   * In production mode, debug messages are disabled by default.
   * They can be enabled in
   * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>.
   * <p>
   * In UsefulTestCase mode, debug messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   *
   * @see #debug(String, Throwable)
   */
  public void debug(@Nullable Throwable t) {
    if (t != null) debug(t.getMessage(), t);
  }

  /**
   * Log a message including a stack trace at debug level.
   * <p>
   * In production mode, debug messages are disabled by default.
   * They can be enabled in
   * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>.
   * <p>
   * In UsefulTestCase mode, debug messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   */
  public abstract void debug(String message, @Nullable Throwable t);

  /**
   * Concatenate the message and all details, without any separator, then log the resulting string.
   * <p>
   * This format differs from {@linkplain #debugValues(String, Collection)} and
   * {@linkplain #error(String, String...)}, which write each detail on a line of its own.
   * <p>
   * In production mode, debug messages are disabled by default.
   * They can be enabled in
   * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>.
   * <p>
   * In UsefulTestCase mode, debug messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   *
   * @param message the first part of the log message, a plain string without any placeholders
   */
  public void debug(@NotNull String message, Object @NotNull ... details) {
    if (isDebugEnabled()) {
      StringBuilder sb = new StringBuilder();
      sb.append(message);
      for (Object detail : details) {
        sb.append(detail);
      }
      debug(sb.toString());
    }
  }

  /**
   * Compose a multi-line log message from the header and the values, writing each value on a line of its own.
   * <p>
   * See {@linkplain #debug(String, Object...)} for a variant using a more compressed format.
   * <p>
   * In production mode, debug messages are disabled by default.
   * They can be enabled in
   * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>.
   * <p>
   * In UsefulTestCase mode, debug messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   *
   * @param header the main log message, a plain string without any placeholders
   */
  public void debugValues(@NotNull String header, @NotNull Collection<?> values) {
    if (isDebugEnabled()) {
      StringBuilder text = new StringBuilder();
      text.append(header).append(" (").append(values.size()).append(")");
      if (!values.isEmpty()) {
        text.append(":");
        for (Object value : values) {
          text.append("\n");
          text.append(value);
        }
      }
      debug(text.toString());
    }
  }

  /**
   * Log the one-line summary of the throwable at info level, and the stack trace at debug level.
   *
   * @see #info(String)
   * @see #debug(Throwable)
   */
  public final void infoWithDebug(@NotNull Throwable t) {
    infoWithDebug(t.toString(), t);
  }

  /**
   * Log the message at info level, and the stack trace at debug level.
   *
   * @see #info(String)
   * @see #debug(Throwable)
   */
  public final void infoWithDebug(@NotNull String message, @NotNull Throwable t) {
    info(message);
    debug(t);
  }

  /**
   * Log the one-line summary of the throwable at warning level, and the stack trace at debug level.
   *
   * @see #warn(String)
   * @see #debug(Throwable)
   */
  public final void warnWithDebug(@NotNull Throwable t) {
    warnWithDebug(t.toString(), t);
  }

  /**
   * Log the message at warning level, and the stack trace at debug level.
   *
   * @see #warn(String)
   * @see #debug(Throwable)
   */
  public final void warnWithDebug(@NotNull String message, @NotNull Throwable t) {
    warn(message);
    debug(t);
  }

  public boolean isTraceEnabled() {
    return isDebugEnabled();
  }

  /**
   * Log a message at trace level, which is finer-grained than debug level.
   * <p>
   * Use this method instead of {@link #debug(String)} for internal events of a subsystem,
   * to avoid overwhelming the log if 'debug' level is enabled.
   * <p>
   * In production mode, trace messages are disabled by default.
   * They can be enabled by appending a <code>:trace</code> suffix in
   * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>
   * <p>
   * In UsefulTestCase mode, trace messages are disabled by default,
   * use {@code TestLoggerFactory.enableTraceLogging} to enable them.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   *
   * @param message should be a plain string literal,
   *                or the call should be enclosed in {@link #isTraceEnabled()}
   */
  public void trace(String message) {
    debug(message);
  }

  /**
   * Log a stack trace at trace level, which is finer-grained than debug level.
   * <p>
   * In production mode, trace messages are disabled by default.
   * They can be enabled by appending a <code>:trace</code> suffix in
   * <a href="https://plugins.jetbrains.com/docs/intellij/ide-infrastructure.html#logging">Help | Diagnostic Tools | Debug Log Settings</a>.
   * <p>
   * In UsefulTestCase mode, trace messages are disabled by default,
   * use {@code TestLoggerFactory.enableTraceLogging} to enable them.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   */
  public void trace(@Nullable Throwable t) {
    debug(t);
  }

  /**
   * Log a stack trace at the info level.
   * <p>
   * In production mode, info messages are enabled by default.
   * <p>
   * In UsefulTestCase mode, info messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   */
  public void info(@NotNull Throwable t) {
    info(t.getMessage(), t);
  }

  /**
   * Log a message at info level.
   * <p>
   * In production mode, info messages are enabled by default.
   * <p>
   * In UsefulTestCase mode, info messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   */
  public void info(String message) {
    info(message, null);
  }

  /**
   * Log a message and a stack trace at info level.
   * <p>
   * In production mode, info messages are enabled by default.
   * <p>
   * In UsefulTestCase mode, info messages are buffered in memory.
   * At the end of a test that fails, these messages go to the console; otherwise, they are dropped.
   */
  public abstract void info(String message, @Nullable Throwable t);

  /**
   * Log a message at warning level.
   * <p>
   * In production mode, warning messages are enabled by default.
   * <p>
   * In UsefulTestCase mode, warning messages go directly to the console.
   */
  public void warn(String message) {
    warn(message, null);
  }

  /**
   * Log a stack trace at the warning level.
   * <p>
   * In production mode, warning messages are enabled by default.
   * <p>
   * In UsefulTestCase mode, warning messages go directly to the console.
   */
  public void warn(@NotNull Throwable t) {
    warn(t.getMessage(), t);
  }

  /**
   * Log a message and a stack trace at the warning level.
   * <p>
   * In production mode, warning messages are enabled by default.
   * <p>
   * In UsefulTestCase mode, warning messages go directly to the console.
   */
  public abstract void warn(String message, @Nullable Throwable t);

  /**
   * Log a message at the error level.
   * <p>
   * In production mode, error messages are enabled by default.
   * In EAP versions, error messages result in an 'IDE Internal Error'.
   * <p>
   * In UsefulTestCase mode, error messages go directly to the console.
   * Additionally, they throw an {@link AssertionError}.
   */
  public void error(String message) {
    error(message, new Throwable(message), ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /** @deprecated use {@link #error(String)} instead and provide a meaningful error message */
  @Deprecated
  public void error(Object message) {
    error(String.valueOf(message));
  }

  static final Function<Attachment, String> ATTACHMENT_TO_STRING = attachment -> attachment.getPath() + "\n" + attachment.getDisplayText();

  public void error(String message, Attachment @NotNull ... attachments) {
    error(message, null, attachments);
  }

  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    String[] result = new String[attachments.length];
    for (int i = 0; i < attachments.length; i++) {
      result[i] = ATTACHMENT_TO_STRING.apply(attachments[i]);
    }
    error(message, t, result);
  }

  /**
   * Compose an error message from the message and the details, then log it.
   * <p>
   * The exact format of the resulting log message depends on the actual logger.
   * The typical format is a multi-line message, with each detail on a line of its own.
   * <p>
   * In production mode, error messages are enabled by default.
   * In EAP versions, error messages result in an 'IDE Internal Error'.
   * <p>
   * In UsefulTestCase mode, error messages go directly to the console.
   * Additionally, they throw an {@link AssertionError}.
   *
   * @param message a plain string, without any placeholders
   */
  public void error(String message, String @NotNull ... details) {
    error(message, new Throwable(message), details);
  }

  /**
   * Log a message and a stack trace at the error level.
   * <p>
   * In production mode, error messages are enabled by default.
   * In EAP versions, error messages result in an 'IDE Internal Error'.
   * <p>
   * In UsefulTestCase mode, error messages go directly to the console.
   * Additionally, they throw an {@link AssertionError}.
   */
  public void error(String message, @Nullable Throwable t) {
    error(message, t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /**
   * Log a stack trace at error level.
   * <p>
   * In production mode, error messages are enabled by default.
   * In EAP versions, error messages result in an 'IDE Internal Error'.
   * <p>
   * In UsefulTestCase mode, error messages go directly to the console.
   * Additionally, they throw an {@link AssertionError}.
   */
  public void error(@NotNull Throwable t) {
    error(t.getMessage(), t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /**
   * Compose an error message from the message and the details, then log it.
   * <p>
   * The exact format of the resulting log message depends on the actual logger.
   * The typical format is a multi-line message, with each detail on a line of its own.
   * <p>
   * In production mode, error messages are enabled by default.
   * In EAP versions, error messages result in an 'IDE Internal Error'.
   * <p>
   * In UsefulTestCase mode, error messages go directly to the console.
   * Additionally, they throw an {@link AssertionError}.
   *
   * @param message a plain string, without any placeholders
   */
  public abstract void error(String message, @Nullable Throwable t, String @NotNull ... details);

  /**
   * Log an error if the condition is false.
   *
   * @param message describes the assertion, in case it failed.
   *                Since the message argument is evaluated even if the condition evaluates to true,
   *                computing the message should be efficient.
   *                String literals and simple field access are fine,
   *                string concatenations should be avoided in places that are called frequently.
   */
  @Contract("false,_->fail") // wrong, but avoid quite a few warnings in the code
  public boolean assertTrue(boolean value, @Nullable Object message) {
    if (!value) {
      String resultMessage = "Assertion failed";
      if (message != null) resultMessage += ": " + message;
      error(resultMessage, new Throwable(resultMessage));
    }

    return value;
  }

  @Contract("false->fail") // wrong, but avoid quite a few warnings in the code
  public boolean assertTrue(boolean value) {
    //noinspection ConstantConditions
    return value || assertTrue(false, null);
  }

  /** @deprecated IntelliJ Platform no longer uses Log4j as the logging framework; please use {@link #setLevel(LogLevel)} instead */
  @Deprecated
  public void setLevel(@SuppressWarnings("unused") @NotNull Level level) {
    error("Do not use, call '#setLevel(LogLevel)' instead");
  }

  public void setLevel(@NotNull LogLevel level) {
    error(getClass() + " should override '#setLevel(LogLevel)'");
  }

  private static final boolean ourRethrowCE = "true".equals(System.getProperty("idea.log.rethrow.ce", "true"));

  public static boolean shouldRethrow(@NotNull Throwable t) {
    return t instanceof ControlFlowException ||
           t instanceof CancellationException && ourRethrowCE;
  }

  @Contract("null -> null; !null -> !null")
  protected static @Nullable Throwable ensureNotControlFlow(@Nullable Throwable t) {
    return t != null && shouldRethrow(t) ?
           new Throwable("Control-flow exceptions (e.g. this " + t.getClass() + ") should never be logged. " +
                         "Instead, these should have been rethrown if caught.", t) :
           t;
  }

  @TestOnly
  public static void setUnitTestMode() {
    isUnitTestMode = true;
  }

  /** {@link #warn(Throwable)} in production, {@link #error(Throwable)} in tests. */
  public void warnInProduction(@NotNull Throwable t) {
    if (isUnitTestMode) {
      error(t);
    }
    else {
      warn(t);
    }
  }
}
