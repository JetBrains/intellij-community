// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.util.ThrowableComputable;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.CancellationException;

import static kotlinx.coroutines.JobKt.ensureActive;

@Internal
public final class Cancellation {

  private Cancellation() { }

  @VisibleForTesting
  public static @Nullable Job currentJob() {
    return contextJob(ThreadContext.currentThreadContext());
  }

  public static @Nullable Job contextJob(@NotNull CoroutineContext context) {
    return context.get(Job.Key);
  }

  public static boolean isCancelled() {
    Job job = currentJob();
    return job != null && job.isCancelled();
  }

  public static void checkCancelled() {
    Job currentJob = currentJob();
    if (currentJob != null) {
      try {
        ensureActive(currentJob);
      }
      catch (CancellationException e) {
        throw new JobCanceledException(e);
      }
    }
  }

  /**
   * Installs the given job as {@link Cancellation#currentJob() current}, runs {@code action}, and returns its result.
   * If the given job becomes cancelled, then {@code ProgressManager#checkCanceled} will throw an instance
   * of the special {@link ProcessCanceledException} subclass inside the given action,
   * and this method will throw the original cancellation exception of the job.
   */
  public static <T, E extends Throwable> T withJob(
    @NotNull Job job,
    @NotNull ThrowableComputable<T, E> action
  ) throws E, CancellationException {
    try (AccessToken ignored = ThreadContext.withThreadContext(job)) {
      return action.compute();
    }
    catch (JobCanceledException e) {
      // This exception is thrown only from `Cancellation.checkCancelled`.
      // If it's caught, then the job must've been cancelled.
      if (!job.isCancelled()) {
        throw new IllegalStateException("JobCanceledException must be thrown by ProgressManager.checkCanceled()", e);
      }
      throw e.getCause();
    }
  }
}
