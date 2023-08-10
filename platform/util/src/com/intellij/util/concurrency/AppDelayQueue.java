// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class implements the global delayed queue which is used by
 * {@link AppScheduledExecutorService} and {@link BoundedScheduledExecutorService}.
 * It starts the background thread which polls the queue for tasks ready to run and sends them to the appropriate executor.
 * The {@link #shutdown(SchedulingWrapper.MyScheduledFutureTask)} must be called before disposal.
 */
final class AppDelayQueue extends DelayQueue<SchedulingWrapper.MyScheduledFutureTask<?>> {
  private static final Logger LOG = Logger.getInstance(AppDelayQueue.class);
  private final TransferThread transferThread = new TransferThread();
  private final AtomicBoolean shutdown = new AtomicBoolean();
  private volatile SchedulingWrapper.MyScheduledFutureTask<Void> myPoisonPill;

  /** this thread takes the ready-to-execute scheduled tasks off the queue and passes them for immediate execution to {@link SchedulingWrapper#backendExecutorService} */
  AppDelayQueue() {
    transferThread.setDaemon(true); // mark as daemon to not prevent JVM to exit (needed for Kotlin CLI compiler)
    transferThread.start();
  }

  void shutdown(@NotNull SchedulingWrapper.MyScheduledFutureTask<Void> poisonPill) {
    if (shutdown.getAndSet(true)) {
      throw new IllegalStateException("Already shutdown");
    }
    myPoisonPill = poisonPill;
    super.offer(poisonPill);
  }

  boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    if (!shutdown.get()) {
      throw new IllegalStateException("must call shutdown before");
    }
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    try {
      myPoisonPill.get(timeout, unit);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    catch (TimeoutException e) {
      return false;
    }
    transferThread.join(TimeUnit.NANOSECONDS.toMillis(Math.max(1, deadline - System.nanoTime())));
    return !transferThread.isAlive();
  }

  @Override
  public boolean offer(@NotNull SchedulingWrapper.MyScheduledFutureTask<?> task) {
    if (shutdown.get()) {
      throw new IllegalStateException("Already shutdown");
    }
    return super.offer(task);
  }

  @NotNull
  Thread getThread() {
    return transferThread;
  }

  private class TransferThread extends Thread {
    private TransferThread() {
      super("Periodic tasks thread");
    }

    @Override
    public void run() {
      while (true) {
        try {
          SchedulingWrapper.MyScheduledFutureTask<?> task = take();
          if (LOG.isTraceEnabled()) {
            LOG.trace("Took "+BoundedTaskExecutor.info(task));
          }
          try {
            if (!task.executeMeInBackendExecutor()) {
              break;
            }
          }
          catch (Throwable e) {
            try {
              LOG.error("Error executing "+task, e);
            }
            catch (Throwable ignored) {
              // do not let it stop the thread
            }
          }
        }
        catch (InterruptedException e) {
          if (!shutdown.get()) {
            LOG.error(e);
          }
        }
      }
      LOG.debug("AppDelayQueue.TransferrerThread Stopped");
    }
  }
}
