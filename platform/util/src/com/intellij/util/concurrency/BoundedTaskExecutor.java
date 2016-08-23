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

import com.intellij.Patches;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ExecutorService which limits the number of tasks running simultaneously.
 * The number of submitted tasks is unrestricted.
 */
public class BoundedTaskExecutor extends AbstractExecutorService {
  private volatile boolean myShutdown;
  @NotNull private final String myName;
  private final Executor myBackendExecutor;
  private final int myMaxTasks;
  // low  32 bits: number of tasks running (or trying to run)
  // high 32 bits: myTaskQueue modification stamp
  private final AtomicLong myStatus = new AtomicLong();
  private final BlockingQueue<Runnable> myTaskQueue = new LinkedBlockingQueue<Runnable>();

  public BoundedTaskExecutor(@NotNull String name, @NotNull Executor backendExecutor, int maxSimultaneousTasks) {
    myName = name;
    myBackendExecutor = backendExecutor;
    if (maxSimultaneousTasks < 1) {
      throw new IllegalArgumentException("maxSimultaneousTasks must be >=1 but got: "+maxSimultaneousTasks);
    }
    if (backendExecutor instanceof BoundedTaskExecutor) {
      throw new IllegalArgumentException("backendExecutor is already BoundedTaskExecutor: "+backendExecutor);
    }
    myMaxTasks = maxSimultaneousTasks;
  }

  /**
   * @deprecated use {@link #BoundedTaskExecutor(String, Executor, int)} instead
   */
  public BoundedTaskExecutor(@NotNull Executor backendExecutor, int maxSimultaneousTasks) {
    this(ExceptionUtil.getThrowableText(new Throwable("Creation point:")), backendExecutor, maxSimultaneousTasks);
  }

  /**
   * Constructor which automatically shuts down this executor when {@code parent} is disposed.
   */
  public BoundedTaskExecutor(@NotNull String name, @NotNull Executor backendExecutor, int maxSimultaneousTasks, @NotNull Disposable parent) {
    this(name, backendExecutor, maxSimultaneousTasks);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        shutdownNow();
      }
    });
  }

  // for diagnostics
  static Object info(Runnable info) {
    Object task = info;
    String extra = null;
    if (task instanceof FutureTask) {
      extra = ((FutureTask)task).isCancelled() ? " (future cancelled)" : ((FutureTask)task).isDone() ? " (future done)" : null;
      task = ObjectUtils.chooseNotNull(ReflectionUtil.getField(task.getClass(), task, Callable.class, "callable"), task);
    }
    if (task instanceof Callable && task.getClass().getName().equals("java.util.concurrent.Executors$RunnableAdapter")) {
      task = ObjectUtils.chooseNotNull(ReflectionUtil.getField(task.getClass(), task, Runnable.class, "task"), task);
    }
    return extra == null ? task : task == null ? extra : task.getClass() + extra;
  }

  @Override
  public void shutdown() {
    if (myShutdown) throw new IllegalStateException("Already shutdown: "+this);
    myShutdown = true;
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    shutdown();
    return clearAndCancelAll();
  }

  @Override
  public boolean isShutdown() {
    return myShutdown;
  }

  @Override
  public boolean isTerminated() {
    return myShutdown;
  }

  // can be executed even after shutdown
  private static class LastTask extends FutureTask<Void> {
    LastTask(@NotNull Runnable runnable) {
      super(runnable, null);
    }
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    if (!isShutdown()) throw new IllegalStateException("you must call shutdown() or shutdownNow() first");
    try {
      waitAllTasksExecuted(timeout, unit);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e.getCause());
    }
    catch (TimeoutException e) {
      return false;
    }
    return true;
  }

  @Override
  public void execute(@NotNull Runnable task) {
    if (isShutdown() && !(task instanceof LastTask)) {
      throw new RejectedExecutionException("Already shutdown");
    }
    long status = incrementCounterAndTimestamp(); // increment inProgress and queue stamp atomically

    int inProgress = (int)status;

    assert inProgress > 0 : inProgress;
    if (inProgress <= myMaxTasks) {
      // optimisation: can execute without queue/dequeue
      wrapAndExecute(task, status);
      return;
    }

    if (!myTaskQueue.offer(task)) {
      throw new RejectedExecutionException();
    }
    Runnable next = pollOrGiveUp(status);
    if (next != null) {
      wrapAndExecute(next, status);
    }
  }

  static {
    assert Patches.USE_REFLECTION_TO_ACCESS_JDK8;
  }
  // todo replace with myStatus.getAndUpdate()
  private long incrementCounterAndTimestamp() {
    long status;
    long newStatus;
    do {
      status = myStatus.get();
      // avoid "tasks number" bits to be garbled on overflow
      newStatus = status + 1 + (1L << 32) & 0x7fffffffffffffffL;
    } while (!myStatus.compareAndSet(status, newStatus));
    return newStatus;
  }

  // return next task taken from the queue if it can be executed now
  // or decrement my counter (atomically) and return null
  private Runnable pollOrGiveUp(long status) {
    while (true) {
      int inProgress = (int)status;
      assert inProgress > 0 : inProgress;

      Runnable next;
      if (inProgress <= myMaxTasks && (next = myTaskQueue.poll()) != null) {
        return next;
      }
      if (myStatus.compareAndSet(status, status - 1)) {
        break;
      }
      status = myStatus.get();
    }
    return null;
  }

  private void wrapAndExecute(@NotNull final Runnable firstTask, final long status) {
    try {
      final AtomicReference<Runnable> currentTask = new AtomicReference<Runnable>(firstTask);
      myBackendExecutor.execute(new Runnable() {
        @Override
        public void run() {
          // we are back inside backend executor, no need to call .execute() - just run synchronously
          Runnable task = currentTask.get();
          do {
            currentTask.set(task);
            try {
              task.run();
            }
            catch (Error ignored) {
              // exception will be stored in this FutureTask status
            }
            catch (RuntimeException ignored) {
              // exception will be stored in this FutureTask status
            }
            task = pollOrGiveUp(status);
          }
          while (task != null);
        }

        @Override
        public String toString() {
          return String.valueOf(info(currentTask.get()));
        }
      });
    }
    catch (Error e) {
      myStatus.decrementAndGet();
      throw e;
    }
    catch (RuntimeException e) {
      myStatus.decrementAndGet();
      throw e;
    }
  }

  public void waitAllTasksExecuted(long timeout, @NotNull TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
    final CountDownLatch started = new CountDownLatch(myMaxTasks);
    final CountDownLatch readyToFinish = new CountDownLatch(1);
    // start myMaxTasks runnables which will spread to all available executor threads
    // and wait for them all to finish
    List<Future> futures = ContainerUtil.map(Collections.nCopies(myMaxTasks, null), new Function<Object, Future>() {
      @Override
      public Future fun(Object o) {
        final LastTask wait = new LastTask(new Runnable() {
          @Override
          public void run() {
            try {
              started.countDown();
              readyToFinish.await();
            }
            catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        });
        execute(wait);
        return wait;
      }
    });
    try {
      if (!started.await(timeout, unit)) {
        throw new TimeoutException("Interrupted by timeout. " + this);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    finally {
      readyToFinish.countDown();
    }
    for (Future future : futures) {
      future.get(timeout, unit);
    }
  }

  @NotNull
  public List<Runnable> clearAndCancelAll() {
    List<Runnable> queued = new ArrayList<Runnable>();
    myTaskQueue.drainTo(queued);
    for (Runnable task : queued) {
      if (task instanceof FutureTask) {
        ((FutureTask) task).cancel(false);
      }
    }
    return queued;
  }

  @Override
  public String toString() {
    return "BoundedExecutor(" + myMaxTasks + ") " + (isShutdown() ? "SHUTDOWN " : "") +
           "inProgress: " + (int)myStatus.get() +
           "; " +
           (myTaskQueue.isEmpty() ? "" : "Queue size: "+myTaskQueue.size() +"; tasks in queue: [" + ContainerUtil.map(myTaskQueue, new Function<Runnable, Object>() {
             @Override
             public Object fun(Runnable runnable) {
               return info(runnable);
             }
           }) + "]") +
      "name: "+myName;
  }
}
