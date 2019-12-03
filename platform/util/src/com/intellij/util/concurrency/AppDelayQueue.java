// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class implements the global delayed queue which is used by
 * {@link AppScheduledExecutorService} and {@link BoundedScheduledExecutorService}.
 * It starts the background thread which polls the queue for tasks ready to run and sends them to the appropriate executor.
 * The {@link #shutdown()} must be called before disposal.
 */
final class AppDelayQueue extends DelayQueue<SchedulingWrapper.MyScheduledFutureTask<?>> {
  private static final Logger LOG = Logger.getInstance(AppDelayQueue.class);
  private final Thread scheduledToPooledTransferrer;
  private final AtomicBoolean shutdown = new AtomicBoolean();

  AppDelayQueue() {
    /* this thread takes the ready-to-execute scheduled tasks off the queue and passes them for immediate execution to {@link SchedulingWrapper#backendExecutorService} */
    scheduledToPooledTransferrer = new Thread(() -> {
      while (!shutdown.get()) {
        try {
          SchedulingWrapper.MyScheduledFutureTask<?> task = take();
          if (LOG.isTraceEnabled()) {
            LOG.trace("Took "+BoundedTaskExecutor.info(task));
          }
          if (!task.isDone()) {  // can be cancelled already
            try {
              task.executeMeInBackendExecutor();
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
        }
        catch (InterruptedException e) {
          if (!shutdown.get()) {
            LOG.error(e);
          }
        }
      }
      LOG.debug("scheduledToPooledTransferrer Stopped");
    }, "Periodic tasks thread");
    scheduledToPooledTransferrer.setDaemon(true); // mark as daemon to not prevent JVM to exit (needed for Kotlin CLI compiler)
    scheduledToPooledTransferrer.start();
  }

  void shutdown() {
    if (shutdown.getAndSet(true)) {
      throw new IllegalStateException("Already shutdown");
    }
    scheduledToPooledTransferrer.interrupt();

    try {
      scheduledToPooledTransferrer.join();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  Thread getThread() {
    return scheduledToPooledTransferrer;
  }
}
