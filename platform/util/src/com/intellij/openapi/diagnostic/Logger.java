// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
 import org.jetbrains.annotations.*;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.function.Function;

/**
 * A standard interface to write to %system%/log/idea.log (or %system%/testlog/idea.log in tests).<p/>
 *
 * In addition to writing to log file, "error" methods result in showing "IDE fatal errors" dialog in the IDE,
 * in EAP versions or if "idea.fatal.error.notification" system property is "true" (). See
 * {@link com.intellij.diagnostic.DefaultIdeaErrorLogger#canHandle} for more details.<p/>
 *
 * Note that in production, a call to "error" doesn't throw exceptions so the execution continues. In tests, however, an {@link AssertionError} is thrown.<p/>
 *
 * In most non-performance tests, debug level is enabled by default, so that when a test fails the full contents of its log are printed to stdout.
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
   * Log a message with 'trace' level which finer-grained than 'debug' level. Use this method instead of {@link #debug(String)} for internal
   * events of a subsystem to avoid overwhelming the log if 'debug' level is enabled.
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

  public void error(String message, String @NotNull ... details) {
    error(message, new Throwable(message), details);
  }

  public void error(String message, @Nullable Throwable t) {
    error(message, t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void error(@NotNull Throwable t) {
    error(t.getMessage(), t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

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

  /**
   * @deprecated IntelliJ Platform no longer uses log4j as the logging framework; please use {@link #setLevel(LogLevel)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
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
           new Throwable("Control-flow exceptions (like " + t.getClass().getSimpleName() + ") should never be logged: " +
                         "ignore for explicitly started processes or rethrow to handle on the outer process level", t) :
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
