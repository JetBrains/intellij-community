/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * @author cdr
 */
public class ConcurrencyUtil {
  /**
   * invokes and waits all tasks using threadPool, avoiding thread starvation on the way
   * @lookat http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html
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
      // force unstarted futures to execute using the current thread
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
   * @return defaultValue if there is no entry in the map (in that case defaultValue is placed into the map), or corresponding value if entry already exists
   */
  @NotNull
  public static <K,V> V cacheOrGet(@NotNull ConcurrentMap<K, V> map, @NotNull final K key, @NotNull final V defaultValue) {
    V prev = map.putIfAbsent(key, defaultValue);
    return prev == null ? defaultValue : prev;
  }

  @NotNull
  public static ThreadPoolExecutor newSingleThreadExecutor(@NotNull @NonNls final String threadFactoryName) {
    return newSingleThreadExecutor(threadFactoryName, Thread.NORM_PRIORITY);
  }

  @NotNull
  public static ThreadPoolExecutor newSingleThreadExecutor(@NotNull final String threadFactoryName, final int threadPriority) {
    return new ThreadPoolExecutor(1, 1,
                                    0L, TimeUnit.MILLISECONDS,
                                    new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
      public Thread newThread(final Runnable r) {
        final Thread thread = new Thread(r, threadFactoryName);
        thread.setPriority(threadPriority);
        return thread;
      }
    });
  }

  @NotNull
  public static ScheduledThreadPoolExecutor newSingleScheduledThreadExecutor(@NotNull @NonNls final String threadFactoryName) {
    return newSingleScheduledThreadExecutor(threadFactoryName, Thread.NORM_PRIORITY);
  }

  @NotNull
  public static ScheduledThreadPoolExecutor newSingleScheduledThreadExecutor(@NotNull final String threadFactoryName, final int threadPriority) {
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
      public Thread newThread(final Runnable r) {
        final Thread thread = new Thread(r, threadFactoryName);
        thread.setPriority(threadPriority);
        return thread;
      }
    });
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return executor;
  }
}
