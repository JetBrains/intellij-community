// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.JobKt;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

import static com.intellij.openapi.progress.Cancellation.currentJob;
import static com.intellij.openapi.progress.Cancellation.withJob;

@Internal
public final class ContextRunnable implements Runnable {

  private final @NotNull CompletableJob myJob;
  private final @NotNull Runnable myRunnable;

  private ContextRunnable(@NotNull Runnable runnable) {
    myJob = JobKt.Job(currentJob());
    myRunnable = runnable;
  }

  @Override
  public void run() {
    try {
      withJob(myJob, () -> {
        myRunnable.run();
        return null;
      });
      myJob.complete();
    }
    catch (CancellationException e) {
      myJob.completeExceptionally(e);
    }
    catch (Throwable e) {
      myJob.completeExceptionally(e);
      throw e;
    }
  }

  /**
   * @see ContextFutureTask#contextCallable
   */
  public static @NotNull Runnable contextRunnable(@NotNull Runnable runnable) {
    if (runnable instanceof ContextFutureTask) {
      return runnable;
    }
    return new ContextRunnable(runnable);
  }
}
