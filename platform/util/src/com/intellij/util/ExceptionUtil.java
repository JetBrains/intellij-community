// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ExceptionUtil {
  private ExceptionUtil() { }

  public static @NotNull Throwable getRootCause(@NotNull Throwable e) {
    while (true) {
      if (e.getCause() == null) {
        return e;
      }
      e = e.getCause();
    }
  }

  public static <T> T findCause(Throwable e, Class<T> klass) {
    return ExceptionUtilRt.findCause(e, klass);
  }

  public static boolean causedBy(Throwable e, Class<?> klass) {
    return ExceptionUtilRt.causedBy(e, klass);
  }

  /**
   * If there are matching throwables both in causes of the {@code error} and in suppressed throwables, causes are guaranteed to be first.
   */
  public static @Unmodifiable <T> List<T> findCauseAndSuppressed(@NotNull Throwable error, @NotNull Class<T> klass) {
    return causeAndSuppressed(error, klass).collect(Collectors.toList());
  }

  /**
   * If there are matching throwables both in causes of the {@code error} and in suppressed throwables, causes are guaranteed to be first.
   */
  @ApiStatus.Internal
  public static <T> @NotNull Stream<T> causeAndSuppressed(@NotNull Throwable error, @NotNull Class<T> klass) {
    Collection<Throwable> allThrowables = new LinkedHashSet<>();
    Deque<Throwable> deque = new ArrayDeque<>();
    deque.add(error);
    while (!deque.isEmpty()) {
      Throwable t = deque.removeFirst();
      if (allThrowables.add(t)) {
        for (Throwable cause = t.getCause(); cause != null; cause = cause.getCause()) {
          deque.addLast(cause);
        }
        for (Throwable s : t.getSuppressed()) {
          deque.addLast(s);
        }
      }
    }
    return allThrowables.stream().filter(klass::isInstance).map(klass::cast);
  }

  public static @NotNull Throwable makeStackTraceRelative(@NotNull Throwable th, @NotNull Throwable relativeTo) {
    StackTraceElement[] trace = th.getStackTrace();
    StackTraceElement[] rootTrace = relativeTo.getStackTrace();
    for (int i = 0, len = Math.min(trace.length, rootTrace.length); i < len; i++) {
      if (trace[trace.length - i - 1].equals(rootTrace[rootTrace.length - i - 1])) continue;
      int newDepth = trace.length - i;
      th.setStackTrace(Arrays.copyOf(trace, newDepth));
      break;
    }
    return th;
  }

  public static @NotNull String currentStackTrace() {
    return getThrowableText(new Throwable());
  }

  public static @NlsSafe @NotNull String getThrowableText(@NotNull Throwable t) {
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    return writer.getBuffer().toString();
  }

  public static @NlsSafe @NotNull String getThrowableText(@NotNull Throwable aThrowable, @NotNull String stackFrameSkipPattern) {
    return ExceptionUtilRt.getThrowableText(aThrowable, stackFrameSkipPattern);
  }

  public static @NlsSafe @NotNull String getUserStackTrace(@NotNull Throwable aThrowable, Logger logger) {
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

  public static @NlsSafe @Nullable String getMessage(@NotNull Throwable e) {
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

  private static @NotNull String extractMessage(@NotNull String result, @NotNull String errorPattern) {
    if (result.lastIndexOf(errorPattern) >= 0) {
      result = result.substring(result.lastIndexOf(errorPattern) + errorPattern.length());
    }
    return result;
  }

  /** Throw t if it is unchecked (RuntimeException or Error), do nothing otherwise */
  public static void rethrowUnchecked(@Nullable Throwable t) throws RuntimeException, Error {
    ExceptionUtilRt.rethrowUnchecked(t);
  }

  @Contract("!null->fail")
  public static void rethrowAll(@Nullable Throwable t) throws Exception {
    ExceptionUtilRt.rethrowAll(t);
  }

  /**
   * Throw throwable as-is, if it is unchecked (RuntimeException or Error), otherwise throw it
   * wrapped in RuntimeException.
   * BEWARE: null argument is still thrown as RuntimeException(cause: null)
   */
  @Contract("_->fail")
  public static void rethrow(@Nullable Throwable throwable) throws RuntimeException, Error {
    rethrowUnchecked(throwable);
    throw new RuntimeException(throwable);
  }

  /**
   * Same as {@link #rethrow(Throwable)}, but t=null is just ignored, instead of throwing a
   * RuntimeException(cause: null)
   */
  @Contract("!null->fail")
  public static void rethrowAllAsUnchecked(@Nullable Throwable t) throws RuntimeException, Error {
    if (t != null) {
      rethrow(t);
    }
  }

  public static @NotNull @NlsSafe String getNonEmptyMessage(@NotNull Throwable t, @NotNull @Nls String defaultMessage) {
    String message = t.getMessage();
    return !StringUtilRt.isEmptyOrSpaces(message) ? message : defaultMessage;
  }

  public static @Nullable Exception runAndCatch(@NotNull ThrowableRunnable<? extends Exception> runnable) {
    try {
      runnable.run();
      return null;
    }
    catch (Exception e) {
      return e;
    }
  }

  /**
   * Runs _all_ the tasks passed in, collect the exceptions risen, and rethrow them.
   * <p/>
   * How exceptions are rethrown depends on their type: we try to throw exception of
   * type E. If the _first_ exception caught is of type E -> we rethrow it, otherwise
   * -> we use exampleSupplier to generate an exception to throw. All other exceptions
   * are added to main exception's 'suppressed' list.
   * <p/>
   * More formally:
   * If the first exception caught is of type E -> the first exception is rethrown with
   * the following exceptions, if any, attached as 'suppressed'.
   * If the first exception caught is NOT of type E -> the 'example' exception is created
   * and thrown, with the first and following exceptions attached as 'suppressed'.
   */
  @SafeVarargs
  public static <E extends Exception>
  void runAllAndRethrowAllExceptions(@NotNull Class<? extends E> exampleClass,
                                     @NotNull Supplier<E> exampleSupplier,
                                     ThrowableRunnable<? extends Exception> @NotNull ... potentiallyFailingTasks) throws E {
    Function<List<? extends Throwable>, E> combiner = exceptions -> {
      E exception = null;
      for (Throwable e : exceptions) {
        if (exception == null) {
          if (exampleClass.isAssignableFrom(e.getClass())) {
            //noinspection unchecked
            exception = (E)e;
          }
          else {
            exception = exampleSupplier.get();
            exception.addSuppressed(e);
          }
        }
        else {
          exception.addSuppressed(e);
        }
      }
      return exception;
    };

    runAllAndRethrowAllExceptions(combiner, potentiallyFailingTasks);
  }


  @SafeVarargs
  @ApiStatus.Internal
  public static <E extends Exception>
  void runAllAndRethrowAllExceptions(@NotNull Function<List<? extends Throwable>, E> exceptionsCombiner,
                                     ThrowableRunnable<? extends Exception> @NotNull ... potentiallyFailingTasks) throws E {
    List<Throwable> exceptions = null;
    for (ThrowableRunnable<? extends Exception> potentiallyFailingTask : potentiallyFailingTasks) {
      try {
        potentiallyFailingTask.run();
      }
      catch (Throwable e) {
        if (exceptions == null) {
          exceptions = new ArrayList<>();
        }
        exceptions.add(e);
      }
    }

    if (exceptions != null) {
      throw exceptionsCombiner.apply(exceptions);
    }
  }

  /**
   * @see ExceptionUtilRt#unwrapException(Throwable, Class)
   */
  public static @NotNull Throwable unwrapException(@NotNull Throwable throwable, @NotNull Class<? extends Throwable> classToUnwrap) {
    return ExceptionUtilRt.unwrapException(throwable, classToUnwrap);
  }
}
