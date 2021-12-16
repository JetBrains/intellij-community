// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.util.ThrowableComputable;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.CancellationException;

@Internal
public final class Cancellation {

  private static final ThreadLocal<Job> ourJob = new ThreadLocal<>();

  private Cancellation() { }

  @VisibleForTesting
  public static @Nullable Job currentJob() {
    return ourJob.get();
  }

  public static boolean isCancelled() {
    Job job = ourJob.get();
    return job != null && job.isCancelled();
  }

  public static void checkCancelled() {
    if (isCancelled()) {
      throw new JobCanceledException();
    }
  }

  /**
   * Makes {@link #isCancelled()} delegate to the passed {@code job} until the returned token is not closed.
   */
  public static @NotNull AccessToken withJob(@NotNull Job job) {
    Job previousJob = ourJob.get();
    ourJob.set(job);
    return new AccessToken() {
      @Override
      public void finish() {
        Job currentJob = ourJob.get();
        ourJob.set(previousJob);
        if (currentJob != job) {
          throw new IllegalStateException("Job was not reset correctly");
        }
      }
    };
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
    try (AccessToken ignored = withJob(job)) {
      return action.compute();
    }
    catch (JobCanceledException e) {
      // This exception is thrown only from `Cancellation.checkCancelled`.
      // If it's caught, then the job must've been cancelled.
      throw job.getCancellationException();
    }
  }
}
