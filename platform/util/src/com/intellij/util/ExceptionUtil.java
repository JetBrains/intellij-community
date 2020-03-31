// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

@SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
public final class ExceptionUtil extends ExceptionUtilRt {
  private ExceptionUtil() { }

  @NotNull
  public static Throwable getRootCause(@NotNull Throwable e) {
    while (true) {
      if (e.getCause() == null) return e;
      e = e.getCause();
    }
  }

  public static <T> T findCause(Throwable e, Class<T> klass) {
    return ExceptionUtilRt.findCause(e, klass);
  }

  public static boolean causedBy(Throwable e, Class<?> klass) {
    return ExceptionUtilRt.causedBy(e, klass);
  }

  @NotNull
  public static Throwable makeStackTraceRelative(@NotNull Throwable th, @NotNull Throwable relativeTo) {
    StackTraceElement[] trace = th.getStackTrace();
    StackTraceElement[] rootTrace = relativeTo.getStackTrace();
    for (int i=0, len = Math.min(trace.length, rootTrace.length); i < len; i++) {
      if (trace[trace.length - i - 1].equals(rootTrace[rootTrace.length - i - 1])) continue;
      int newDepth = trace.length - i;
      th.setStackTrace(Arrays.copyOf(trace, newDepth));
      break;
    }
    return th;
  }

  @NotNull
  public static String currentStackTrace() {
    return getThrowableText(new Throwable());
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable t) {
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    return writer.getBuffer().toString();
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable, @NotNull String stackFrameSkipPattern) {
    return ExceptionUtilRt.getThrowableText(aThrowable, stackFrameSkipPattern);
  }

  @NotNull
  public static String getUserStackTrace(@NotNull Throwable aThrowable, Logger logger) {
    String result = getThrowableText(aThrowable, "com.intellij.");
    if (!result.contains("\n\tat") && aThrowable.getStackTrace().length > 0) {
      // no 3rd party stack frames found, log as error
      logger.error(aThrowable);
    }
    else {
      return result.trim() + " (no stack trace)";
    }
    return result;
  }

  @Nullable
  public static String getMessage(@NotNull Throwable e) {
    String result = e.getMessage();
    String exceptionPattern = "Exception: ";
    String errorPattern = "Error: ";

    while (e.getCause() != null && (result == null || result.contains(exceptionPattern) || result.contains(errorPattern))) {
      e = e.getCause();
      result = e.getMessage();
    }

    if (result != null) {
      result = extractMessage(result, exceptionPattern);
      result = extractMessage(result, errorPattern);
    }

    return result;
  }

  @NotNull
  private static String extractMessage(@NotNull String result, @NotNull String errorPattern) {
    if (result.lastIndexOf(errorPattern) >= 0) {
      result = result.substring(result.lastIndexOf(errorPattern) + errorPattern.length());
    }
    return result;
  }

  public static void rethrowUnchecked(@Nullable Throwable t) {
    ExceptionUtilRt.rethrowUnchecked(t);
  }

  @Contract("!null->fail")
  public static void rethrowAll(@Nullable Throwable t) throws Exception {
    ExceptionUtilRt.rethrowAll(t);
  }

  @Contract("_->fail")
  public static void rethrow(@Nullable Throwable throwable) {
    rethrowUnchecked(throwable);
    throw new RuntimeException(throwable);
  }

  @Contract("!null->fail")
  public static void rethrowAllAsUnchecked(@Nullable Throwable t) {
    if (t != null) {
      rethrow(t);
    }
  }

  @NotNull
  public static String getNonEmptyMessage(@NotNull Throwable t, @NotNull String defaultMessage) {
    String message = t.getMessage();
    return !StringUtil.isEmptyOrSpaces(message) ? message : defaultMessage;
  }
}