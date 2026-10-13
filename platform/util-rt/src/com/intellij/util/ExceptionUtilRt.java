// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

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
   * Returns an iterator over the exception and its causes.
   * <p>
   * Always yields the first element at the beginning.
   * <p>
   * In case of a cycle, the iterator stops right before the first repeated element.
   */
  @ApiStatus.Experimental
  public static Iterator<Throwable> causeChain(@NotNull Throwable start) {
    return new CauseChainIterator(start);
  }

  private static class CauseChainIterator implements Iterator<Throwable> {
    private final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private @Nullable Throwable current;
    private boolean loaded = false;

    CauseChainIterator(@NotNull Throwable start) {
      current = start;
    }

    @Override
    public boolean hasNext() {
      if (!loaded) {
        loaded = true;
        if (current != null && !seen.add(current)) {
          current = null;
        }
      }
      return current != null;
    }

    @Override
    public Throwable next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      Throwable r = current;
      current = current.getCause();
      loaded = false;
      return r;
    }
  }

  static Iterable<Throwable> cycleAwareCauseChain(@Nullable Throwable start) {
    if (start != null) {
      start = start.getCause();
    }
    if (start == null) return Collections.emptyList();
    final Throwable start1 = start;
    return new Iterable<Throwable>() {
      @Override
      public @NotNull Iterator<Throwable> iterator() {
        return causeChain(start1);
      }
    };
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
