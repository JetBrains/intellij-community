// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import kotlinx.coroutines.CompletableJob;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.progress.Cancellation.withJob;

@Internal
public final class PeriodicCancellationRunnable implements Runnable {

  private final @NotNull CompletableJob myJob;
  private final @NotNull Runnable myRunnable;

  public PeriodicCancellationRunnable(@NotNull CompletableJob job, @NotNull Runnable runnable) {
    myJob = job;
    myRunnable = runnable;
  }

  @Override
  public void run() {
    try {
      withJob(myJob, () -> {
        myRunnable.run();
        return null;
      });
      // don't complete the job, it can be either failed, or cancelled
    }
    catch (Throwable e) {
      myJob.completeExceptionally(e);
      throw e;
    }
  }
}
