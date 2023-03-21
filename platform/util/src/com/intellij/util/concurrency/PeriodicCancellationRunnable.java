// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import kotlinx.coroutines.CompletableJob;
import org.jetbrains.annotations.NotNull;

final class PeriodicCancellationRunnable implements Runnable {

  private final @NotNull CompletableJob myJob;
  private final @NotNull Runnable myRunnable;

  PeriodicCancellationRunnable(@NotNull CompletableJob job, @NotNull Runnable runnable) {
    myJob = job;
    myRunnable = runnable;
  }

  @Override
  public void run() {
    try {
      myRunnable.run();
      // don't complete the job, it can be either failed, or cancelled
    }
    catch (Throwable e) {
      myJob.completeExceptionally(e);
      throw e;
    }
  }
}
