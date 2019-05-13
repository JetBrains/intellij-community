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
            super.println("\tin " + stripPackage(x, skipPattern.length()));
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
}
