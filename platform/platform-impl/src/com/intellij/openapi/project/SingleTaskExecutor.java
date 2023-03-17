// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Progressive;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * An executor that can run exactly one task. The task can be re-started many times, but there is at most one running task at any moment.
 * <p>
 * {@linkplain #tryStartProcess(Consumer)} should be invoked to start execution.
 * <p>
 * It is safe to invoke {@link #tryStartProcess(Consumer)} many times, and it is guaranteed that no more than one task is executing at any moment.
 * <p>
 * It is guaranteed that after {@linkplain  #tryStartProcess(Consumer)} the task will be executed at least one more time. Task can reset scheduled
 * executions by invoking {@linkplain #clearScheduledFlag()}
 */
class SingleTaskExecutor {
  interface AutoclosableProgressive extends AutoCloseable, Progressive {

    @Override
    void close();
  }

  private class StateAwareTask implements AutoclosableProgressive {
    private final AtomicBoolean used = new AtomicBoolean(false);
    private final Progressive task;

    private StateAwareTask(Progressive task) { this.task = task; }

    @Override
    public void close() {
      if (used.compareAndSet(false, true)) {
        runWithStateHandling(EmptyRunnable.getInstance());
      }
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (used.compareAndSet(false, true)) {
        runWithStateHandling(() -> task.run(indicator));
      }
      else {
        LOG.error("StateAwareTask cannot be reused");
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(SingleTaskExecutor.class);

  private enum RUN_STATE {STOPPED, STARTING, RUNNING, STOPPING}

  private final AtomicReference<RUN_STATE> runState = new AtomicReference<>(RUN_STATE.STOPPED);

  private final AtomicBoolean shouldContinueBackgroundProcessing = new AtomicBoolean(false);
  private final Progressive task;

  SingleTaskExecutor(Progressive task) {
    this.task = task;
  }

  private void runWithStateHandling(Runnable runnable) {
    try {
      do {
        try {
          RUN_STATE oldState = runState.getAndSet(RUN_STATE.RUNNING);
          LOG.assertTrue(oldState == RUN_STATE.STARTING, "Old state should be STARTING, but was " + oldState);
          // shouldContinueBackgroundProcessing is normally cleared before reading next item from the queue.
          // Here we clear the flag just in case, if runnable fail to clear the flag (e.g. during cancellation)
          shouldContinueBackgroundProcessing.set(false);
          runnable.run();
        }
        finally {
          RUN_STATE oldState = runState.getAndSet(RUN_STATE.STOPPING);
          LOG.assertTrue(oldState == RUN_STATE.RUNNING, "Old state should be RUNNING, but was " + oldState);
        }
      }
      while (shouldContinueBackgroundProcessing.get() && runState.compareAndSet(RUN_STATE.STOPPING, RUN_STATE.STARTING));
    } finally {
      runState.compareAndSet(RUN_STATE.STOPPING, RUN_STATE.STOPPED);
    }
  }

  /**
   * Generates a task that will be fed into {@code #processRunner}. Consumer must invoke `run` or `close` on the task. New invocations
   * of this method will have no effect until previous task completes either of its `run` or `closed` methods
   *
   * @param processRunner receiver for the task that must be executed by consumer (in any thread).
   * @return true if current thread won the competition and started processing
   */
  final boolean tryStartProcess(Consumer<AutoclosableProgressive> processRunner) {
    if (!shouldContinueBackgroundProcessing.compareAndSet(false, true)) {
      return false; // the thread that set shouldContinueBackgroundProcessing (not this thread) should compete with the background thread
    }
    if (runState.get() == RUN_STATE.RUNNING) {
      return false; // there will be at least one more check of shouldContinueBackgroundProcessing in the background thread
    }
    else {
      boolean thisThreadShouldProcessQueue = runState.compareAndSet(RUN_STATE.STOPPING, RUN_STATE.STARTING) ||
                                             runState.compareAndSet(RUN_STATE.STOPPED, RUN_STATE.STARTING);
      // whatever thread (this or background) wins the competition and sets STARTING - that thread should process the queue
      if (!thisThreadShouldProcessQueue) return false;
    }

    processRunner.accept(new StateAwareTask(task));
    return true;
  }

  public void clearScheduledFlag() {
    shouldContinueBackgroundProcessing.set(false);
  }

  public boolean isRunning() {
    return runState.get() != RUN_STATE.STOPPED;
  }
}