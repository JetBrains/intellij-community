// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author nik
 */
public class ExceptionUtilRt {
  public static void rethrowUnchecked(@Nullable Throwable t) {
    if (t instanceof Error) throw (Error)t;
    if (t instanceof RuntimeException) throw (RuntimeException)t;
  }

  @Contract("!null->fail")
  public static void rethrowAll(@Nullable Throwable t) throws Exception {
    if (t != null) {
      rethrowUnchecked(t);
      throw (Exception)t;
    }
  }

  public static <T> T findCause(Throwable e, Class<T> klass) {
    while (e != null && !klass.isInstance(e)) {
      e = e.getCause();
    }
    //noinspection unchecked
    return (T)e;
  }

  public static boolean causedBy(Throwable e, Class klass) {
    return findCause(e, klass) != null;
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
      private boolean newLine;

      @Override
      public void print(String x) {
        if (x == null) return;
        boolean curSkipping = skipping;
        if (!skipping && x.startsWith(skipPattern)) curSkipping = true;
        else if (skipping && !x.startsWith(prefix)) curSkipping = false;
        if (curSkipping) {
          if (!skipping) {
            super.print("\tin " + stripPackage(x, skipPattern.length()));
            newLine = true;
          }
          skipping = !x.startsWith(prefixRemoteUtil);
        }
        else if (!x.startsWith(prefixProxy)) {
          super.print(x);
          newLine = true;
        }
        skipping = curSkipping;
      }

      @Override
      public void println() {
        if (newLine) {
          newLine = false;
          super.println();
        }
      }
    };
    aThrowable.printStackTrace(writer);
    return stringWriter.toString();
  }

  private static String stripPackage(String x, int offset) {
    int idx = offset;
    while (idx > 0 && idx < x.length() && !Character.isUpperCase(x.charAt(idx))) {
      idx = x.indexOf('.', idx) + 1;
    }
    return x.substring(Math.max(idx, offset));
  }
}
