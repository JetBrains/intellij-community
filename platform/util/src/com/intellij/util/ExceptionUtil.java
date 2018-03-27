/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

public class ExceptionUtil {
  private ExceptionUtil() { }

  @NotNull
  public static Throwable getRootCause(@NotNull Throwable e) {
    while (true) {
      if (e.getCause() == null) return e;
      e = e.getCause();
    }
  }

  public static <T> T findCause(Throwable e, Class<T> klass) {
    while (e != null && !klass.isInstance(e)) {
      e = e.getCause();
    }
    @SuppressWarnings("unchecked") T t = (T)e;
    return t;
  }

  public static boolean causedBy(Throwable e, Class klass) {
    return findCause(e, klass) != null;
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
  public static String getThrowableText(@NotNull Throwable aThrowable) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    aThrowable.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable, @NotNull String stackFrameSkipPattern) {
    final String prefix = "\tat ";
    final String prefixProxy = prefix + "$Proxy";
    final String prefixRemoteUtil = prefix + "com.intellij.execution.rmi.RemoteUtil";
    final String skipPattern = prefix + stackFrameSkipPattern;

    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter) {
      private boolean skipping;
      @Override
      public void println(final String x) {
        boolean curSkipping = skipping;
        if (x != null) {
          if (!skipping && x.startsWith(skipPattern)) curSkipping = true;
          else if (skipping && !x.startsWith(prefix)) curSkipping = false;
          if (curSkipping && !skipping) {
            super.println("\tin "+ stripPackage(x, skipPattern.length()));
          }
          skipping = curSkipping;
          if (skipping) {
            skipping = !x.startsWith(prefixRemoteUtil);
            return;
          }
          if (x.startsWith(prefixProxy)) return;
          super.println(x);
        }
      }
    };
    aThrowable.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
  }

  private static String stripPackage(String x, int offset) {
    int idx = offset;
    while (idx > 0 && idx < x.length() && !Character.isUpperCase(x.charAt(idx))) {
      idx = x.indexOf('.', idx) + 1;
    }
    return x.substring(Math.max(idx, offset));
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

    while ((result == null || result.contains(exceptionPattern) || result.contains(errorPattern)) && e.getCause() != null) {
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
    if (t instanceof Error) throw (Error)t;
    if (t instanceof RuntimeException) throw (RuntimeException)t;
  }

  public static void rethrowAll(@Nullable Throwable t) throws Exception {
    if (t != null) {
      rethrowUnchecked(t);
      throw (Exception)t;
    }
  }

  public static void rethrow(@Nullable Throwable throwable) {
    rethrowUnchecked(throwable);
    throw new RuntimeException(throwable);
  }

  public static void rethrowAllAsUnchecked(@Nullable Throwable t) {
    if (t != null) {
      rethrowUnchecked(t);
      throw new RuntimeException(t);
    }
  }

  @NotNull
  public static String getNonEmptyMessage(@NotNull Throwable t, @NotNull String defaultMessage) {
    String message = t.getMessage();
    return !StringUtil.isEmptyOrSpaces(message) ? message : defaultMessage;
  }
}