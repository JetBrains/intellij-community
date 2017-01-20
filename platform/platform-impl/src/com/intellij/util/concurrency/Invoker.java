/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Expirable;
import com.intellij.util.containers.TransferToEDTQueue;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.openapi.util.Disposer.register;
import static javax.swing.SwingUtilities.isEventDispatchThread;

/**
 * @author Sergey.Malenkov
 */
public abstract class Invoker implements Disposable {
  private static final Logger LOG = Logger.getInstance(Invoker.class);
  private static final AtomicInteger UID = new AtomicInteger();
  private final AtomicInteger count = new AtomicInteger();
  private final String description;
  volatile boolean disposed;

  private Invoker(String prefix, Disposable parent) {
    description = UID.getAndIncrement() + ".Invoker." + prefix + ":" + parent.getClass().getName();
    register(parent, this);
  }

  @Override
  public String toString() {
    return description;
  }

  @Override
  public void dispose() {
    disposed = true;
  }

  /**
   * Returns {@code true} if the current thread allows to process a task.
   *
   * @return {@code true} if the current thread is valid, or {@code false} otherwise
   */
  public abstract boolean isValidThread();

  /**
   * Invokes the specified task asynchronously on the valid thread.
   * Even if this method is called from the valid thread
   * the specified task will still be deferred
   * until all pending events have been processed.
   *
   * @param task a task to execute asynchronously on the valid thread
   */
  public final void invokeLater(@NotNull Runnable task) {
    if (canInvoke(task)) {
      count.incrementAndGet();
      offer(() -> invokeSafely(task));
    }
  }

  /**
   * Invokes the specified task immediately if the current thread is valid,
   * or asynchronously after all pending tasks have been processed.
   *
   * @param task a task to execute on the valid thread
   */
  public final void invokeLaterIfNeeded(@NotNull Runnable task) {
    if (isValidThread()) {
      count.incrementAndGet();
      invokeSafely(task);
    }
    else {
      invokeLater(task);
    }
  }

  /**
   * Returns a workload of the task queue.
   *
   * @return amount of tasks, which are executing or waiting for execution
   */
  public final int getTaskCount() {
    return disposed ? 0 : count.get();
  }

  abstract void offer(Runnable runnable);

  final void invokeSafely(Runnable task) {
    try {
      if (canInvoke(task)) task.run();
    }
    catch (Exception exception) {
      LOG.warn(exception);
    }
    finally {
      count.decrementAndGet();
    }
  }

  final boolean canInvoke(Runnable task) {
    if (disposed) {
      LOG.debug("Invoker is disposed");
      return false;
    }
    if (task instanceof Expirable) {
      Expirable expirable = (Expirable)task;
      if (expirable.isExpired()) {
        LOG.debug("Task is expired");
        return false;
      }
    }
    return true;
  }

  /**
   * This class is the {@code Invoker} in the Event Dispatch Thread,
   * which is the only one valid thread for this invoker.
   */
  public static final class EDT extends Invoker {
    private final TransferToEDTQueue<Runnable> queue;

    public EDT(@NotNull Disposable parent) {
      this(parent, 200);
    }

    public EDT(@NotNull Disposable parent, int maxUnitOfWorkThresholdMs) {
      super("EDT", parent);
      queue = TransferToEDTQueue.createRunnableMerger(toString(), maxUnitOfWorkThresholdMs);
    }

    @Override
    public void dispose() {
      super.dispose();
      queue.stop();
    }

    @Override
    public boolean isValidThread() {
      return isEventDispatchThread();
    }

    @Override
    void offer(Runnable runnable) {
      queue.offer(runnable);
    }
  }

  /**
   * This class is the {@code Invoker} in a background thread pool.
   * Every thread is valid for this invoker except the EDT.
   * It allows to run background tasks in parallel,
   * but requires a good synchronization.
   */
  public static final class BackgroundPool extends Invoker {
    public BackgroundPool(@NotNull Disposable parent) {
      super("Background.Pool", parent);
    }

    @Override
    public boolean isValidThread() {
      return !isEventDispatchThread();
    }

    @Override
    void offer(Runnable runnable) {
      AppExecutorUtil.getAppExecutorService().submit(runnable);
    }
  }

  /**
   * This class is the {@code Invoker} in a single background thread.
   * This invoker does not need additional synchronization.
   */
  public static final class BackgroundThread extends Invoker {
    private final ExecutorService executor;
    private volatile Thread thread;

    public BackgroundThread(@NotNull Disposable parent) {
      super("Background.Thread", parent);
      executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(toString(), 1);
    }

    @Override
    public void dispose() {
      super.dispose();
      executor.shutdown();
    }

    @Override
    public boolean isValidThread() {
      return thread == Thread.currentThread();
    }

    @Override
    void offer(Runnable runnable) {
      executor.execute(() -> {
        thread = Thread.currentThread();
        runnable.run();
        thread = null;
      });
    }
  }
}
