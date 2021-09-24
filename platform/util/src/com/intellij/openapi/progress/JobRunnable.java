// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress;

import com.intellij.openapi.application.AccessToken;
import kotlinx.coroutines.CompletableDeferred;
import kotlinx.coroutines.CompletableJob;
import kotlinx.coroutines.JobKt;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

import static com.intellij.openapi.progress.Cancellation.currentJob;
import static com.intellij.openapi.progress.Cancellation.withJob;

@Internal
public final class JobRunnable implements Runnable {

  private final @NotNull CompletableJob myJob;
  private final @NotNull Runnable myRunnable;

  private JobRunnable(@NotNull Runnable runnable) {
    myJob = JobKt.Job(currentJob());
    myRunnable = runnable;
  }

  @Override
  public void run() {
    try (AccessToken ignored = withJob(myJob)) {
      myRunnable.run();
      myJob.complete();
    }
    catch (JobCanceledException e) {
      if (!myJob.isCancelled()) {
        throw new IllegalStateException("JobCanceledException must be thrown by ProgressManager.checkCanceled()", e);
      }
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
   * @see JobFutureTask#jobCallable(CompletableDeferred, Callable)
   */
  public static @NotNull Runnable jobRunnable(@NotNull Runnable runnable) {
    if (runnable instanceof JobFutureTask) {
      return runnable;
    }
    return new JobRunnable(runnable);
  }
}
