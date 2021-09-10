// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.ThrowableRunnable;
import kotlinx.coroutines.Job;
import kotlinx.coroutines.JobKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public final class Cancellation {

  private static final ThreadLocal<Job> ourJob = new ThreadLocal<>();

  private Cancellation() { }

  @TestOnly
  public static boolean isCancelled() {
    Job job = ourJob.get();
    return job != null && job.isCancelled();
  }

  public static void checkCancelled() {
    Job job = ourJob.get();
    if (job != null) {
      JobKt.ensureActive(job);
    }
  }

  /**
   * Makes {@link #checkCancelled()} delegate to the passed {@code job} until the returned token is not closed.
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

  public static <E extends Throwable> void withJob(@NotNull Job job, @NotNull ThrowableRunnable<E> action) throws E {
    try (AccessToken ignored = withJob(job)) {
      action.run();
    }
  }

  public static <T, E extends Throwable> T withJob(@NotNull Job job, @NotNull ThrowableComputable<T, E> action) throws E {
    try (AccessToken ignored = withJob(job)) {
      return action.compute();
    }
  }
}
