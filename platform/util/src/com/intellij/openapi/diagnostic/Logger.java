/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.diagnostic;

import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;

public abstract class Logger {
  public interface Factory {
    @NotNull
    Logger getLoggerInstance(@NotNull String category);
  }

  private static class DefaultFactory implements Factory {
    @NotNull
    @Override
    public Logger getLoggerInstance(@NotNull String category) {
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

  public static boolean isInitialized() {
    return !(ourFactory instanceof DefaultFactory);
  }

  @NotNull
  public static Logger getInstance(@NotNull String category) {
    return ourFactory.getLoggerInstance(category);
  }

  @NotNull
  public static Logger getInstance(@NotNull Class cl) {
    return getInstance("#" + cl.getName());
  }

  public abstract boolean isDebugEnabled();

  public abstract void debug(String message);

  public abstract void debug(@Nullable Throwable t);

  public abstract void debug(String message, @Nullable Throwable t);

  public void debug(@NotNull String message, @NotNull Object... details) {
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
    error(message, new Throwable(message), ArrayUtil.EMPTY_STRING_ARRAY);
  }
  public void error(Object message) {
    error(String.valueOf(message));
  }

  static final Function<Attachment, String> ATTACHMENT_TO_STRING = new Function<Attachment, String>() {
    @Override
    public String fun(Attachment attachment) {
      return attachment.getPath() + "\n" + attachment.getDisplayText();
    }
  };

  public void error(String message, @NotNull Attachment... attachments) {
    error(message, null, attachments);
  }

  public void error(String message, @Nullable Throwable t, @NotNull Attachment... attachments) {
    error(message, t, ContainerUtil.map2Array(attachments, String.class, ATTACHMENT_TO_STRING));
  }

  public void error(String message, @NotNull String... details) {
    error(message, new Throwable(message), details);
  }

  public void error(String message, @Nullable Throwable t) {
    error(message, t, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public void error(@NotNull Throwable t) {
    error(t.getMessage(), t, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  public abstract void error(String message, @Nullable Throwable t, @NotNull String... details);

  @Contract("false,_->fail") // wrong, but avoid quite a few warnings in the code
  public boolean assertTrue(boolean value, @Nullable Object message) {
    if (!value) {
      String resultMessage = "Assertion failed";
      if (message != null) resultMessage += ": " + message;
      error(resultMessage, new Throwable(resultMessage));
    }

    //noinspection Contract
    return value;
  }

  @Contract("false->fail") // wrong, but avoid quite a few warnings in the code
  public boolean assertTrue(boolean value) {
    //noinspection ConstantConditions
    return value || assertTrue(false, null);
  }

  public abstract void setLevel(Level level);

  protected static Throwable checkException(@Nullable Throwable t) {
    return t instanceof ControlFlowException ? new Throwable("Control-flow exceptions should never be logged", t) : t;
  }
}