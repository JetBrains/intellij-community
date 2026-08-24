// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.NoSuchElementException;

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

  /**
   * Walks {@code e}'s cause chain looking for an instance of {@code klass}, using {@link #cycleAwareCauseChain} so a chain
   * that loops back on itself terminates instead of spinning forever.
   * <p>
   * Returns {@code null} on a cycle: {@code klass} was never found before the chain looped back, which is the
   * same answer as "not found" for an acyclic chain.
   */
  public static <T> T findCause(Throwable e, Class<T> klass) {
    if (klass.isInstance(e)) {
      //noinspection unchecked
      return (T)e;
    }
    for (Throwable t : cycleAwareCauseChain(e)) {
      if (klass.isInstance(t)) {
        //noinspection unchecked
        return (T)t;
      }
    }
    return null;
  }

  public static boolean causedBy(Throwable e, Class<?> klass) {
    return findCause(e, klass) != null;
  }

  /**
   * The causes strictly after {@code start} (not {@code start} itself), stopping when the chain ends or Floyd's
   * two-pointer ("tortoise and hare") cycle detection catches it looping back on an earlier node. Single-use: the
   * result is its own {@link Iterator}, so iterating it twice yields nothing the second time.
   */
  static Iterable<Throwable> cycleAwareCauseChain(@Nullable Throwable start) {
    return new CauseChain(start);
  }

  private static final class CauseChain implements Iterable<Throwable>, Iterator<Throwable> {
    private Throwable current;
    private Throwable slow;
    private boolean advanceSlow;

    private CauseChain(@Nullable Throwable start) {
      current = start;
      slow = start;
    }

    @Override
    public Iterator<Throwable> iterator() {
      return this;
    }

    @Override
    public boolean hasNext() {
      if (current == null) return false;
      Throwable cause = current.getCause();
      return cause != null && cause != current;
    }

    @Override
    public Throwable next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      Throwable cause = current.getCause();
      current = cause;
      if (advanceSlow) {
        slow = slow.getCause();
        if (slow == current) {
          current = null; // cycle detected: stop the walk here, same as reaching the end of the chain
        }
      }
      advanceSlow = !advanceSlow;
      return cause;
    }
  }

  @NotNull
  public static <T extends Throwable> T addRethrownStackAsSuppressed(@NotNull T throwable) {
    if (!(throwable instanceof RethrownStack)) {
      throwable.addSuppressed(new RethrownStack());
    }
    return throwable;
  }

  private static class RethrownStack extends Throwable {
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
    // If the cause chain ends in a cycle, returns the last exception visited.
    Throwable last = throwable;
    for (Throwable cause : cycleAwareCauseChain(throwable)) {
      if (!classToUnwrap.isInstance(last)) break;
      last = cause;
    }
    return last;
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
