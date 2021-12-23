// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

import com.intellij.concurrency.ThreadContext;
import com.intellij.openapi.application.AccessToken;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CompletableJob;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

import static com.intellij.openapi.progress.Cancellation.withJob;
import static kotlinx.coroutines.JobKt.Job;

@Internal
public final class ContextRunnable implements Runnable {

  private final @NotNull CoroutineContext myParentContext;
  private final @NotNull CompletableJob myJob;
  private final @NotNull Runnable myRunnable;

  private ContextRunnable(@NotNull Runnable runnable) {
    myParentContext = ThreadContext.currentThreadContext();
    myJob = Job(Cancellation.contextJob(myParentContext));
    myRunnable = runnable;
  }

  @Override
  public void run() {
    ThreadContext.checkUninitializedThreadContext();
    try (AccessToken ignored = ThreadContext.resetThreadContext(myParentContext)) {
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
    return new ContextRunnable(runnable);
  }
}
