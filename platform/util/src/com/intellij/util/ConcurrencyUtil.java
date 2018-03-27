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
package com.intellij.util;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.diagnostic.ThreadDumper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author cdr
 */
public class ConcurrencyUtil {
  /**
   * Invokes and waits all tasks using threadPool, avoiding thread starvation on the way
   * (see <a href="http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html">"A Thread Pool Puzzler"</a>).
   */
  public static <T> List<Future<T>> invokeAll(@NotNull Collection<Callable<T>> tasks, ExecutorService executorService) throws Throwable {
    if (executorService == null) {
      for (Callable<T> task : tasks) {
        task.call();
      }
      return null;
    }

    List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
    boolean done = false;
    try {
      for (Callable<T> t : tasks) {
        Future<T> future = executorService.submit(t);
        futures.add(future);
      }
      // force not started futures to execute using the current thread
      for (Future f : futures) {
        ((Runnable)f).run();
      }
      for (Future f : futures) {
        try {
          f.get();
        }
        catch (CancellationException ignore) {
        }
        catch (ExecutionException e) {
          Throwable cause = e.getCause();
          if (cause != null) {
            throw cause;
          }
        }
      }
      done = true;
    }
    finally {
      if (!done) {
        for (Future f : futures) {
          f.cancel(false);
        }
      }
    }
    return futures;
  }

  /**
   * @return defaultValue if there is no entry in the map (in that case defaultValue is placed into the map),
   *         or corresponding value if entry already exists.
   */
  @NotNull
  public static <K, V> V cacheOrGet(@NotNull ConcurrentMap<K, V> map, @NotNull final K key, @NotNull final V defaultValue) {
    V v = map.get(key);
    if (v != null) return v;
    V prev = map.putIfAbsent(key, defaultValue);
    return prev == null ? defaultValue : prev;
  }

  /**
   * @return defaultValue if the reference contains null (in that case defaultValue is placed there), or reference value otherwise.
   */
  @ReviseWhenPortedToJDK("8") // todo "replace with return ref.updateAndGet(prev -> prev == null ? defaultValue : prev)"
  @NotNull
  public static <T> T cacheOrGet(@NotNull AtomicReference<T> ref, @NotNull T defaultValue) {
    T value = ref.get();
    while (value == null) {
      value = ref.compareAndSet(null, defaultValue) ? defaultValue : ref.get();
    }
    return value;
  }

  @NotNull
  public static ThreadPoolExecutor newSingleThreadExecutor(@NotNull @NonNls String name) {
    return newSingleThreadExecutor(name, Thread.NORM_PRIORITY);
  }

  @NotNull
  public static ThreadPoolExecutor newSingleThreadExecutor(@NonNls @NotNull String name, int priority) {
    return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                                  new LinkedBlockingQueue<Runnable>(), newNamedThreadFactory(name, true, priority));
  }

  @NotNull
  public static ScheduledThreadPoolExecutor newSingleScheduledThreadExecutor(@NotNull @NonNls String name) {
    return newSingleScheduledThreadExecutor(name, Thread.NORM_PRIORITY);
  }

  @NotNull
  public static ScheduledThreadPoolExecutor newSingleScheduledThreadExecutor(@NonNls @NotNull String name, int priority) {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, newNamedThreadFactory(name, true, priority));
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return executor;
  }

  @NotNull
  public static ThreadFactory newNamedThreadFactory(@NonNls @NotNull final String name, final boolean isDaemon, final int priority) {
    return new ThreadFactory() {
      @NotNull
      @Override
      public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r, name);
        thread.setDaemon(isDaemon);
        thread.setPriority(priority);
        return thread;
      }
    };
  }

  @NotNull
  public static ThreadFactory newNamedThreadFactory(@NonNls @NotNull final String name) {
    return new ThreadFactory() {
      @NotNull
      @Override
      public Thread newThread(@NotNull final Runnable r) {
        return new Thread(r, name);
      }
    };
  }

  /**
   * Awaits for all tasks in the {@code executor} to finish for the specified {@code timeout}
   */
  @TestOnly
  public static void awaitQuiescence(@NotNull ThreadPoolExecutor executor, long timeout, @NotNull TimeUnit unit) {
    executor.setKeepAliveTime(1, TimeUnit.NANOSECONDS); // no need for zombies in tests
    executor.setCorePoolSize(0); // interrupt idle workers
    ReentrantLock mainLock = ReflectionUtil.getField(executor.getClass(), executor, ReentrantLock.class, "mainLock");
    Set workers;
    mainLock.lock();
    try {
      HashSet workersField = ReflectionUtil.getField(executor.getClass(), executor, HashSet.class, "workers");
      workers = new HashSet(workersField); // to be able to iterate thread-safely outside the lock
    }
    finally {
      mainLock.unlock();
    }
    for (Object worker : workers) {
      Thread thread = ReflectionUtil.getField(worker.getClass(), worker, Thread.class, "thread");
      try {
        thread.join(unit.toMillis(timeout));
      }
      catch (InterruptedException e) {
        String trace = "Thread leaked: " + thread+"; " + thread.getState()+" ("+ thread.isAlive()+")\n--- its stacktrace:\n";
        for (final StackTraceElement stackTraceElement : thread.getStackTrace()) {
          trace += " at "+stackTraceElement +"\n";
        }
        trace += "---\n";
        System.err.println("Executor " + executor + " is still active after " + unit.toSeconds(timeout) + " seconds://///\n" +
                           "Thread "+thread+" dump:\n" + trace+
                           "all thread dump:\n"+ThreadDumper.dumpThreadsToString() + "\n/////");
        break;
      }
    }
  }

  public static void joinAll(@NotNull Collection<? extends Thread> threads) throws RuntimeException {
    for (Thread thread : threads) {
      try {
        thread.join();
      }
      catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
  public static void joinAll(@NotNull Thread... threads) throws RuntimeException {
    joinAll(Arrays.asList(threads));
  }

  public static void runUnderThreadName(@NotNull String name, @NotNull Runnable runnable) {
    String oldThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName(name);
    try {
      runnable.run();
    }
    finally {
      Thread.currentThread().setName(oldThreadName);
    }
  }
}
