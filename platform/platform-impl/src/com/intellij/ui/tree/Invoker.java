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
package com.intellij.ui.tree;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Expirable;
import com.intellij.util.concurrency.QueueProcessor;
import com.intellij.util.containers.TransferToEDTQueue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Sergey.Malenkov
 */
abstract class Invoker implements Disposable {
  private static final Logger LOG = Logger.getInstance(Invoker.class);
  private static final AtomicInteger UID = new AtomicInteger();
  private final AtomicInteger count = new AtomicInteger();
  private final String description;
  volatile boolean disposed;

  private Invoker(String prefix, String name) {
    description = "Invoker." + prefix + ":" + name + " " + UID.getAndIncrement();
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
  public static class EDT extends Invoker {
    private final TransferToEDTQueue<Runnable> queue;

    public EDT(@NotNull @NonNls String name) {
      this(name, 200);
    }

    public EDT(@NotNull @NonNls String name, int maxUnitOfWorkThresholdMs) {
      super("EDT", name);
      queue = TransferToEDTQueue.createRunnableMerger(toString(), maxUnitOfWorkThresholdMs);
    }

    @Override
    public void dispose() {
      super.dispose();
      queue.stop();
    }

    @Override
    public boolean isValidThread() {
      return SwingUtilities.isEventDispatchThread();
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
  public static class Background extends Invoker {
    public Background(@NotNull @NonNls String name) {
      super("Background", name);
    }

    @Override
    public boolean isValidThread() {
      return !SwingUtilities.isEventDispatchThread();
    }

    @Override
    void offer(Runnable runnable) {
      Application application = ApplicationManager.getApplication();
      if (application != null) {
        application.executeOnPooledThread(runnable);
      }
      else {
        PooledThreadExecutor.INSTANCE.submit(runnable);
      }
    }
  }

  /**
   * This class is the {@code Invoker} in a random background thread.
   * No thread is valid for this invoker, because it adds tasks to the queue.
   * This invoker does not need additional synchronization.
   */
  public static class BackgroundQueue extends Invoker {
    private final QueueProcessor<Runnable> queue;

    public BackgroundQueue(@NotNull @NonNls String name) {
      super("Background", name);
      queue = QueueProcessor.createRunnableQueueProcessor();
    }

    @Override
    public void dispose() {
      super.dispose();
      queue.clear();
    }

    @Override
    public boolean isValidThread() {
      return false;
    }

    @Override
    void offer(Runnable runnable) {
      queue.add(runnable);
    }
  }
}
