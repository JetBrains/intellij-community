// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.util.ThrowableComputable;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.CancellationException;
import java.util.function.Supplier;

import static kotlinx.coroutines.JobKt.ensureActive;

@Internal
public final class Cancellation {

  private Cancellation() { }

  @VisibleForTesting
  public static @Nullable Job currentJob() {
    return ThreadContext.currentThreadContext().get(Job.Key);
  }

  public static void checkCancelled() {
    ThreadContext.warnAccidentalCancellation();
    Job currentJob = currentJob();
    if (currentJob != null) {
      try {
        ensureActive(currentJob);
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (CancellationException e) {
        throw new CeProcessCanceledException(e);
      }
    }
  }

  /**
   * {@code true} if running in a non-cancelable section started with {@link #computeInNonCancelableSection} in this thread,
   * otherwise {@code false}
   */
  // do not supply initial value to conserve memory
  private static final ThreadLocal<Boolean> isInNonCancelableSection = new ThreadLocal<>();

  public static boolean isInNonCancelableSection() {
    return isInNonCancelableSection.get() != null;
  }

  public static <T, E extends Exception> T computeInNonCancelableSection(@NotNull ThrowableComputable<T, E> computable) throws E {
    try {
      if (isInNonCancelableSection()) {
        return computable.compute();
      }
      try {
        isInNonCancelableSection.set(Boolean.TRUE);
        return computable.compute();
      }
      finally {
        isInNonCancelableSection.remove();
      }
    }
    catch (ProcessCanceledException e) {
      throw new RuntimeException("PCE is not expected in non-cancellable section execution", e);
    }
  }

  public static void executeInNonCancelableSection(@NotNull Runnable runnable) {
    computeInNonCancelableSection(() -> {
      runnable.run();
      return null;
    });
  }

  public static @NotNull AccessToken withNonCancelableSection() {
    if (isInNonCancelableSection()) {
      return AccessToken.EMPTY_ACCESS_TOKEN;
    }

    isInNonCancelableSection.set(Boolean.TRUE);
    return new AccessToken() {
      @Override
      public void finish() {
        isInNonCancelableSection.remove();
      }
    };
  }

  /**
   * Throwing {@code CancellationException} from {@code <clinit>} causes {@link ExceptionInInitializerError} and bricks the class forever.
   * For example, requesting services or connecting a message bus are cancellable operations,
   * and they must not be invoked from class initializers.
   * Avoiding complex operations in initializers makes the classloading faster.
   *
   * @deprecated To prevent the new code from accidentally calling cancellable APIs,
   * we need to enable the error logging by default.
   * To do that, existing failures have to be fixed right away or suppressed.
   * This method exists to suppress existing failures in our own tests.
   * It serves as a point for finding all such cases by searching for its usages.
   * This method will throw an error in coming releases.
   * <a href="https://youtrack.jetbrains.com/issue/IJPL-1045">YouTrack issue.</a>
   */
  @Internal
  @Deprecated
  public static <T> T forceNonCancellableSectionInClassInitializer(@NotNull Supplier<T> computable) {
    return computeInNonCancelableSection(computable::get);
  }
}
