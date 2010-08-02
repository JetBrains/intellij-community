/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionUtil {
  public static Throwable getRootCause(Throwable e) {
    while (true) {
      if (e.getCause() == null) return e;
      e = e.getCause();
    }
  }

  public static <T extends Throwable> T findCause(Throwable e, Class<T> klass) {
    while (e != null && !klass.isInstance(e)) {
      e = e.getCause();
    }
    return (T)e;
  }

  public static boolean causedBy(Throwable e, Class klass) {
    return findCause(e, klass) != null;
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    aThrowable.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
  }

  @NotNull
  public static String getThrowableText(@NotNull Throwable aThrowable, @NonNls @NotNull final String stackFrameSkipPattern) {
    @NonNls final String prefix = "\tat ";
    final String skipPattern = prefix + stackFrameSkipPattern;
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter writer = new PrintWriter(stringWriter) {
      boolean skipping = false;
      public void println(final String x) {
        if (x != null) {
          if (!skipping && x.startsWith(skipPattern)) skipping = true;
          else if (skipping && !x.startsWith(prefix)) skipping = false;
        }
        if (skipping) return;
        super.println(x);
      }
    };
    aThrowable.printStackTrace(writer);
    return stringWriter.getBuffer().toString();
  }

  public static String getMessage(@NotNull Throwable e) {
    String result = e.getMessage();
    @NonNls final String exceptionPattern = "Exception: ";
    @NonNls final String errorPattern = "Error: ";

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
  private static String extractMessage(@NotNull String result, @NotNull final String errorPattern) {
    if (result.lastIndexOf(errorPattern) >= 0) {
      result = result.substring(result.lastIndexOf(errorPattern) + errorPattern.length());
    }
    return result;
  }
}
