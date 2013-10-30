/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BoundedTaskExecutor implements Executor {
  protected final Executor myBackendExecutor;
  private final int myMaxTasks;
  private final AtomicInteger myInProgress = new AtomicInteger(0);
  private final Queue<FutureTask> myTaskQueue = new LinkedBlockingQueue<FutureTask>();

  private final Runnable USER_TASK_RUNNER = new Runnable() {
    @Override
    public void run() {
      final FutureTask task = myTaskQueue.poll();
      try {
        if (task != null && !task.isCancelled()) {
          task.run();
        }
      }
      finally {
        myInProgress.decrementAndGet();
        if (!myTaskQueue.isEmpty()) {
          processQueue();
        }
      }
    }
  };

  public BoundedTaskExecutor(Executor backendExecutor, int maxSimultaneousTasks) {
    myBackendExecutor = backendExecutor;
    myMaxTasks = Math.max(maxSimultaneousTasks, 1);
  }

  @Override
  public void execute(@NotNull Runnable task) {
    submit(task);
  }

  public Future<?> submit(Runnable task) {
    final RunnableFuture<Void> future = queueTask(new FutureTask<Void>(task, null));
    if (future == null) {
      throw new RuntimeException("Failed to queue task: " + task);
    }
    return future;
  }

  public <T> Future<T> submit(Callable<T> task) {
    final RunnableFuture<T> future = queueTask(new FutureTask<T>(task));
    if (future == null) {
      throw new RuntimeException("Failed to queue task: " + task);
    }
    return future;
  }

  @Nullable
  private <T> RunnableFuture<T> queueTask(FutureTask<T> futureTask) {
    if (myTaskQueue.offer(futureTask)) {
      processQueue();
      return futureTask;
    }
    return null;
  }

  protected void processQueue() {
    while (true) {
      final int count = myInProgress.get();
      if (count >= myMaxTasks) {
        return;
      }
      if (myInProgress.compareAndSet(count, count + 1)) {
        break;
      }
    }
    myBackendExecutor.execute(USER_TASK_RUNNER);
  }
}
