// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import kotlinx.coroutines.CompletableJob;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

import static com.intellij.util.concurrency.Propagation.runAsCoroutine;

final class PeriodicCancellationRunnable implements Runnable {

  private final @NotNull CompletableJob myJob;
  private final @NotNull Runnable myRunnable;

  PeriodicCancellationRunnable(@NotNull CompletableJob job, @NotNull Runnable runnable) {
    myJob = job;
    myRunnable = runnable;
  }

  @Override
  public void run() {
    // don't complete the job, it can be either failed, or cancelled
    try {
      runAsCoroutine(myJob, false, () -> {
        myRunnable.run();
        return null;
      });
    } catch (CancellationException e) {
      // According to the specification of the FutureTask, the runnable should not throw in case of cancellation.
      // Instead, Java relies on interruptions
      // This does not go along with the coroutines framework rules, but we have to play Java rules here
      if (!myJob.isCancelled()) {
        throw e;
      }
    }
  }
}
