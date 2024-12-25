// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

public final class ExceptionUtilRt {

  private ExceptionUtilRt() {}

  public static void rethrowUnchecked(@Nullable Throwable t) throws RuntimeException, Error {
    if (t instanceof Error) throw addRethrownStackAsSuppressed((Error)t);
    if (t instanceof RuntimeException) throw addRethrownStackAsSuppressed((RuntimeException)t);
  }

  @Contract("!null->fail")
  public static void rethrowAll(@Nullable Throwable t) throws Exception {
    if (t != null) {
      rethrowUnchecked(t);
      throw addRethrownStackAsSuppressed((Exception)t);
    }
  }

  public static <T> T findCause(Throwable e, Class<T> klass) {
    while (e != null && !klass.isInstance(e)) {
      e = e.getCause();
    }
    //noinspection unchecked
    return (T)e;
  }

  public static boolean causedBy(Throwable e, Class<?> klass) {
    return findCause(e, klass) != null;
  }
  
  public static <T extends Throwable> T addRethrownStackAsSuppressed(T throwable) {
    throwable.addSuppressed(new RethrownStack());
    return throwable;
  }

  static class RethrownStack extends Throwable {
    RethrownStack() {
      super("Rethrown at");
    }
  }

  /**
   * @param throwable exception to unwrap
   * @param classToUnwrap exception class to unwrap
   * @return the supplied exception, or unwrapped exception (if the supplied exception class is classToUnwrap)
   */
  @NotNull
  public static Throwable unwrapException(@NotNull Throwable throwable, @NotNull Class<? extends Throwable> classToUnwrap) {
    while (classToUnwrap.isInstance(throwable) && throwable.getCause() != null && throwable.getCause() != throwable) {
      throwable = throwable.getCause();
    }
    return throwable;
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
