// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.function.Function;

/**
 * <p>A standard interface to write to {@code %system%/log/idea.log} (or {@code %system%/testlog/idea.log} in tests).
 *
 * <p>The {@code error} methods, in addition to writing to the log file,
 * result in showing the "IDE fatal errors" dialog in the IDE
 * (in EAP versions or if the {@code idea.fatal.error.notification} system property is set to {@code true}).
 * See {@link com.intellij.diagnostic.DefaultIdeaErrorLogger#canHandle} for more details.
 *
 * <p>The {@code error} methods, when run in unit test mode, throw an {@linkplain AssertionError}.
 * In production, they do not throw exceptions, so the execution continues.
 *
 * <p>In most non-performance tests, the debug level is enabled by default -
 * so that when a test fails, the full content of its log is printed to stdout.
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
    System.out.println("Changing log factory from " + ourFactory.getClass().getCanonicalName() +
                       " to " + factory.getCanonicalName() + '\n' + ExceptionUtil.getThrowableText(new Throwable()));
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

  public abstract void debug(String message);

  public abstract void debug(@Nullable Throwable t);

  public abstract void debug(String message, @Nullable Throwable t);

  /**
   * Concatenate the message and all details, without any separator, then log the resulting string.
   * <p>
   * This format differs from {@linkplain #debugValues(String, Collection)} and
   * {@linkplain #error(String, String...)}, which write each detail on a line of its own.
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

  public final void infoWithDebug(@NotNull Throwable t) {
    infoWithDebug(t.toString(), t);
  }

  public final void infoWithDebug(@NotNull String message, @NotNull Throwable t) {
    info(message);
    debug(t);
  }

  public final void warnWithDebug(@NotNull Throwable t) {
    warnWithDebug(t.toString(), t);
  }

  public final void warnWithDebug(@NotNull String message, @NotNull Throwable t) {
    warn(message);
    debug(t);
  }

  public boolean isTraceEnabled() {
    return isDebugEnabled();
  }

  /**
   * Log a message with 'trace' level, which is finer-grained than the 'debug' level.
   *
   * <p>Use this method instead of {@link #debug(String)} for internal events of a subsystem,
   * to avoid overwhelming the log if 'debug' level is enabled.
   */
  public void trace(String message) {
    debug(message);
  }

  public void trace(@Nullable Throwable t) {
    debug(t);
  }

  public void info(@NotNull Throwable t) {
    info(t.getMessage(), t);
  }

  public abstract void info(String message);

  public abstract void info(String message, @Nullable Throwable t);

  public void warn(String message) {
    warn(message, null);
  }

  public void warn(@NotNull Throwable t) {
    warn(t.getMessage(), t);
  }

  public abstract void warn(String message, @Nullable Throwable t);

  public void error(String message) {
    error(message, new Throwable(message), ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void error(Object message) {
    error(String.valueOf(message));
  }

  static final Function<Attachment, String> ATTACHMENT_TO_STRING = attachment -> attachment.getPath() + "\n" + attachment.getDisplayText();

  public void error(String message, Attachment @NotNull ... attachments) {
    error(message, null, attachments);
  }

  public void error(String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    error(message, t, ContainerUtil.map2Array(attachments, String.class, ATTACHMENT_TO_STRING::apply));
  }

  /**
   * Compose an error message from the message and the details, then log it.
   * <p>
   * The exact format of the resulting log message depends on the actual logger.
   * The typical format is a multi-line message, with each detail on a line of its own.
   *
   * @param message a plain string, without any placeholders
   */
  public void error(String message, String @NotNull ... details) {
    error(message, new Throwable(message), details);
  }

  public void error(String message, @Nullable Throwable t) {
    error(message, t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void error(@NotNull Throwable t) {
    error(t.getMessage(), t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  /**
   * Compose an error message from the message and the details, then log it.
   * <p>
   * The exact format of the resulting log message depends on the actual logger.
   * The typical format is a multi-line message, with each detail on a line of its own.
   *
   * @param message a plain string, without any placeholders
   */
  public abstract void error(String message, @Nullable Throwable t, String @NotNull ... details);

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
  public abstract void setLevel(@NotNull Level level);

  public void setLevel(@NotNull LogLevel level) {
    switch (level) {
      case OFF:
        setLevel(Level.OFF);
        break;
      case ERROR:
        setLevel(Level.ERROR);
        break;
      case WARNING:
        setLevel(Level.WARN);
        break;
      case INFO:
        setLevel(Level.INFO);
        break;
      case DEBUG:
        setLevel(Level.DEBUG);
        break;
      case TRACE:
        setLevel(Level.TRACE);
        break;
      case ALL:
        setLevel(Level.ALL);
        break;
    }
  }

  protected static Throwable ensureNotControlFlow(@Nullable Throwable t) {
    return t instanceof ControlFlowException ?
           new Throwable("Control-flow exceptions (e.g. this " + t.getClass() + ") should never be logged. " +
                         "Instead, these should have been rethrown or, if not possible, caught and ignored", t) :
           t;
  }

  @TestOnly
  public static void setUnitTestMode() {
    isUnitTestMode = true;
  }

  /**
   * {@link #warn} in production, {@link #error} in tests
   */
  public void warnInProduction(@NotNull Throwable t) {
    if (isUnitTestMode) {
      error(t);
    }
    else {
      warn(t);
    }
  }
}
