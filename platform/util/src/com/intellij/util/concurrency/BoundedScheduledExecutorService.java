// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Creates a bounded {@link ScheduledExecutorService} out of passed regular {@link ExecutorService}.
 * The created {@link ScheduledExecutorService} allows to {@link #schedule(Callable, long, TimeUnit)} tasks later
 * and execute them in parallel in the {@code backendExecutor} not more than {@code maxSimultaneousTasks} at a time.
 * It's assumed that the lifecycle of {@code backendExecutor} is not affected by this class, so calling the {@link #shutdown()} on {@link BoundedScheduledExecutorService} doesn't shut down the {@code backendExecutor}.
 */
class BoundedScheduledExecutorService extends SchedulingWrapper {
  BoundedScheduledExecutorService(@NotNull @NonNls String name, @NotNull ExecutorService backendExecutor, int maxThreads) {
    super(new BoundedTaskExecutor(name, backendExecutor, maxThreads, true),
          ((AppScheduledExecutorService)AppExecutorUtil.getAppScheduledExecutorService()).delayQueue);
    assert !(backendExecutor instanceof ScheduledExecutorService) : "backendExecutor is already ScheduledExecutorService: " + backendExecutor;
  }

  @Override
  void onDelayQueuePurgedOnShutdown() {
    // we control backendExecutorService lifecycle, so we should shut it down ourselves
    backendExecutorService.shutdown(); // only after this task bubbles through the AppDelayQueue allow backendExecutorService to shut down to let all in-flight tasks be executed before that
  }

  @Override
  public @NotNull List<Runnable> shutdownNow() {
    List<Runnable> runnables = super.shutdownNow();
    return ContainerUtil.concat(runnables, backendExecutorService.shutdownNow());
  }
}
