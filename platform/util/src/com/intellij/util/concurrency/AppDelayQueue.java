/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class implements the global delayed queue which is used by
 * {@link AppScheduledExecutorService} and {@link BoundedScheduledExecutorService}.
 * It starts the background thread which polls the queue for tasks ready to run and sends them to the appropriate executor.
 * The {@link #shutdown()} must be called before disposal.
 */
class AppDelayQueue extends DelayQueue<SchedulingWrapper.MyScheduledFutureTask> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.AppDelayQueue");
  private final Thread scheduledToPooledTransferer;
  private final AtomicBoolean shutdown = new AtomicBoolean();

  AppDelayQueue() {
    /** this thread takes the ready-to-execute scheduled tasks off the queue and passes them for immediate execution to {@link SchedulingWrapper#backendExecutorService} */
    scheduledToPooledTransferer = new Thread(new Runnable() {
      @Override
      public void run() {
        while (!shutdown.get()) {
          try {
            final SchedulingWrapper.MyScheduledFutureTask task = take();
            if (LOG.isTraceEnabled()) {
              LOG.trace("Took "+BoundedTaskExecutor.info(task));
            }
            task.getBackendExecutorService().execute(task);
          }
          catch (InterruptedException e) {
            if (!shutdown.get()) {
              LOG.error(e);
            }
          }
        }
        LOG.debug("scheduledToPooledTransferer Stopped");
      }
    }, "Periodic tasks thread");
    scheduledToPooledTransferer.start();
  }

  void shutdown() {
    if (shutdown.getAndSet(true)) {
      throw new IllegalStateException("Already shutdown");
    }
    scheduledToPooledTransferer.interrupt();

    try {
      scheduledToPooledTransferer.join();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
