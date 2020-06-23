// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diagnostic;

import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
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
  public interface Factory {
    @NotNull
    Logger getLoggerInstance(@NotNull String category);
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

      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("Changing log factory\n" + ExceptionUtil.getThrowableText(new Throwable()));
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
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("Changing log factory\n" + ExceptionUtil.getThrowableText(new Throwable()));
    }

    ourFactory = factory;
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

  public abstract void debug(@NonNls String message);

  public abstract void debug(@Nullable Throwable t);

  public abstract void debug(@NonNls String message, @Nullable Throwable t);

  public void debug(@NonNls @NotNull String message, Object @NotNull ... details) {
    if (isDebugEnabled()) {
      StringBuilder sb = new StringBuilder();
      sb.append(message);
      for (Object detail : details) {
        sb.append(detail);
      }
      debug(sb.toString());
    }
  }

  public boolean isTraceEnabled() {
    return isDebugEnabled();
  }

  /**
   * Log a message with 'trace' level which finer-grained than 'debug' level. Use this method instead of {@link #debug(String)} for internal
   * events of a subsystem to avoid overwhelming the log if 'debug' level is enabled.
   */
  public void trace(@NonNls String message) {
    debug(message);
  }

  public void trace(@Nullable Throwable t) {
    debug(t);
  }

  public void info(@NotNull Throwable t) {
    info(t.getMessage(), t);
  }

  public abstract void info(@NonNls String message);

  public abstract void info(@NonNls String message, @Nullable Throwable t);

  public void warn(@NonNls String message) {
    warn(message, null);
  }

  public void warn(@NotNull Throwable t) {
    warn(t.getMessage(), t);
  }

  public abstract void warn(@NonNls String message, @Nullable Throwable t);

  public void error(@NonNls String message) {
    error(message, new Throwable(message), ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void error(Object message) {
    error(String.valueOf(message));
  }

  static final Function<Attachment, String> ATTACHMENT_TO_STRING = attachment -> attachment.getPath() + "\n" + attachment.getDisplayText();

  public void error(@NonNls String message, Attachment @NotNull ... attachments) {
    error(message, null, attachments);
  }

  public void error(@NonNls String message, @Nullable Throwable t, Attachment @NotNull ... attachments) {
    error(message, t, ContainerUtil.map2Array(attachments, String.class, ATTACHMENT_TO_STRING::apply));
  }

  public void error(@NonNls String message, String @NotNull ... details) {
    error(message, new Throwable(message), details);
  }

  public void error(@NonNls String message, @Nullable Throwable t) {
    error(message, t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public void error(@NotNull Throwable t) {
    error(t.getMessage(), t, ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  public abstract void error(@NonNls String message, @Nullable Throwable t, String @NotNull ... details);

  @Contract("false,_->fail") // wrong, but avoid quite a few warnings in the code
  public boolean assertTrue(boolean value, @NonNls @Nullable Object message) {
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

  public abstract void setLevel(Level level);

  protected static Throwable checkException(@Nullable Throwable t) {
    return t instanceof ControlFlowException ? new Throwable(
      "Control-flow exceptions (like " + t.getClass().getSimpleName() + ") should never be logged", t) : t;
  }
}