/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ExecutorService which limits the number of tasks running simultaneously.
 * The number of submitted tasks is unrestricted.
 */
public class BoundedTaskExecutor extends AbstractExecutorService {
  private volatile boolean myShutdown;
  private final Executor myBackendExecutor;
  private final int myMaxTasks;
  // number of tasks running (or trying to run)
  private final AtomicInteger myInProgress = new AtomicInteger();
  private final BlockingQueue<Runnable> myTaskQueue = new LinkedBlockingQueue<Runnable>();

  public BoundedTaskExecutor(@NotNull Executor backendExecutor, int maxSimultaneousTasks) {
    myBackendExecutor = backendExecutor;
    if (maxSimultaneousTasks < 1) {
      throw new IllegalArgumentException("maxSimultaneousTasks must be >=1 but got: "+maxSimultaneousTasks);
    }
    myMaxTasks = maxSimultaneousTasks;
  }

  /**
   * Constructor which automatically shuts down this executor when {@code parent} is disposed.
   */
  public BoundedTaskExecutor(@NotNull Executor backendExecutor, int maxSimultaneousTasks, @NotNull Disposable parent) {
    this(backendExecutor, maxSimultaneousTasks);
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        shutdownNow();
      }
    });
  }

  // for diagnostics
  public static Object info(Object task) {
    if (task instanceof FutureTask) {
      task = ReflectionUtil.getField(task.getClass(), task, Callable.class, "callable");
    }
    if (task instanceof Callable && task.getClass().getName().equals("java.util.concurrent.Executors$RunnableAdapter")) {
      task = ReflectionUtil.getField(task.getClass(), task, Runnable.class, "task");
    }
    return task;
  }

  @Override
  public void shutdown() {
    if (myShutdown) throw new IllegalStateException("Already shutdown");
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

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    if (!isShutdown()) throw new IllegalStateException("you must call shutdown() first");
    return true;
  }

  @Override
  public void execute(@NotNull Runnable task) {
    myTaskQueue.offer(task);
    int inProgress = myInProgress.incrementAndGet();

    tryToPollAndExecuteNext(inProgress);
  }

  private void tryToPollAndExecuteNext(int inProgress) {
    while (!isShutdown()) {
      assert inProgress > 0 : inProgress;
      Runnable next;
      if (inProgress <= myMaxTasks && (next = myTaskQueue.poll()) != null) {
        try {
          myBackendExecutor.execute(wrap(next));
        }
        catch (Error e) {
          myInProgress.decrementAndGet();
          throw e;
        }
        catch (RuntimeException e) {
          myInProgress.decrementAndGet();
          throw e;
        }
        break;
      }
      if (myInProgress.compareAndSet(inProgress, inProgress-1)) {
        break;
      }
      inProgress = myInProgress.get();
    }
  }

  @NotNull
  private Runnable wrap(@NotNull final Runnable task) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          task.run();
        }
        finally {
          tryToPollAndExecuteNext(myInProgress.get());
        }
      }

      @Override
      public String toString() {
        return String.valueOf(info(task));
      }
    };
  }

  @TestOnly
  public void waitAllTasksExecuted(int timeout, @NotNull TimeUnit unit) throws ExecutionException, InterruptedException {
    final CountDownLatch started = new CountDownLatch(myMaxTasks);
    final CountDownLatch readyToFinish = new CountDownLatch(1);
    // start myMaxTasks runnables which will spread to all available executor threads
    // and wait for them all to finish
    List<Future> futures = ContainerUtil.map(Collections.nCopies(myMaxTasks, null), new Function<Object, Future>() {
      @Override
      public Future fun(Object o) {
        return submit(new Runnable() {
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
      }
    });
    try {
      if (!started.await(timeout, unit)) {
        throw new RuntimeException("Interrupted by timeout. " + this +
                                   "; Thread dump:\n" + ThreadDumper.dumpThreadsToString());
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    readyToFinish.countDown();
    for (Future future : futures) {
      future.get();
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
           "inProgress: " + myInProgress +
           "; tasks in queue: [" + ContainerUtil.map(myTaskQueue, new Function<Runnable, Object>() {
      @Override
      public Object fun(Runnable runnable) {
        return info(runnable);
      }
    }) +"]";
  }
}
