// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class ConcurrencyUtil {

  public static final long DEFAULT_TIMEOUT_MS = 10;

  /**
   * Invokes and waits for all tasks using {@code executorService}, avoiding thread starvation on the way.
   * (see <a href="http://gafter.blogspot.com/2006/11/thread-pool-puzzler.html">"A Thread Pool Puzzler"</a>).
   */
  public static <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks, ExecutorService executorService) throws Throwable {
    if (executorService == null) {
      for (Callable<T> task : tasks) {
        task.call();
      }
      return null;
    }

    List<Future<T>> futures = new ArrayList<>(tasks.size());
    boolean done = false;
    try {
      for (Callable<T> t : tasks) {
        Future<T> future = executorService.submit(t);
        futures.add(future);
      }
      // force not yet started futures to execute using the current thread
      for (Future<?> f : futures) {
        ((Runnable)f).run();
      }
      for (Future<?> f : futures) {
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
        for (Future<?> f : futures) {
          f.cancel(false);
        }
      }
    }
    return futures;
  }

  /**
   * @return defaultValue if there is no entry in the map (in that case, defaultValue is placed into the map),
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
  @NotNull
  public static <T> T cacheOrGet(@NotNull AtomicReference<T> ref, @NotNull T defaultValue) {
    T value = ref.get();
    if (value != null) return value;
    return ref.updateAndGet(prev -> prev == null ? defaultValue : prev);
  }

  @NotNull
  public static ThreadPoolExecutor newSingleThreadExecutor(@NotNull @NonNls String name) {
    return newSingleThreadExecutor(name, Thread.NORM_PRIORITY);
  }

  @NotNull
  public static ThreadPoolExecutor newSingleThreadExecutor(@NonNls @NotNull String name, int priority) {
    return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                                  new LinkedBlockingQueue<>(), newNamedThreadFactory(name, true, priority));
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

  /**
   * Service, which executes tasks synchronously, immediately after they submitted
   */
  @NotNull
  public static ExecutorService newSameThreadExecutorService() {
    return new SameThreadExecutorService();
  }

  @NotNull
  public static ThreadFactory newNamedThreadFactory(@NonNls @NotNull final String name, final boolean isDaemon, final int priority) {
    return r -> {
      Thread thread = new Thread(r, name);
      thread.setDaemon(isDaemon);
      thread.setPriority(priority);
      return thread;
    };
  }

  @NotNull
  public static ThreadFactory newNamedThreadFactory(@NonNls @NotNull final String name) {
    return r -> new Thread(r, name);
  }

  /**
   * Awaits for all tasks in the {@code executor} to finish for the specified {@code timeout}
   */
  @TestOnly
  public static void awaitQuiescence(@NotNull ThreadPoolExecutor executor, long timeout, @NotNull TimeUnit unit) {
    executor.setKeepAliveTime(1, TimeUnit.NANOSECONDS); // no need for zombies in tests
    executor.setCorePoolSize(0); // interrupt idle workers
    ReentrantLock mainLock = ReflectionUtil.getField(executor.getClass(), executor, ReentrantLock.class, "mainLock");
    Set<Object> workers;
    mainLock.lock();
    try {
      @SuppressWarnings("unchecked")
      Set<Object> workersField = ReflectionUtil.getField(executor.getClass(), executor, HashSet.class, "workers");
      // to be able to iterate thread-safely outside the lock
      workers = new HashSet<>(workersField);
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
        @NonNls String trace = "Thread leaked: " + thread + "; " + thread.getState() + " (" + thread.isAlive() + ")\n--- its stacktrace:\n";
        for (final StackTraceElement stackTraceElement : thread.getStackTrace()) {
          trace += " at "+stackTraceElement+"\n";
        }
        trace += "---\n";
        @NonNls String message = "Executor " + executor + " is still active after " + unit.toSeconds(timeout) + " seconds://///\n" +
                                 "Thread " + thread + " dump:\n" + trace +
                                 "all thread dump:\n" + ThreadDumper.dumpThreadsToString() + "\n/////";
        System.err.println(message);
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
  public static void joinAll(Thread @NotNull ... threads) throws RuntimeException {
    joinAll(Arrays.asList(threads));
  }
  public static void getAll(@NotNull Collection<? extends Future<?>> futures) throws ExecutionException, InterruptedException {
    for (Future<?> future : futures) {
      future.get();
    }
  }
  public static void getAll(long timeout, @NotNull TimeUnit timeUnit, @NotNull Collection<? extends @NotNull Future<?>> futures)
    throws ExecutionException, InterruptedException, TimeoutException {
    long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
    for (Future<?> future : futures) {
      long toWait = deadline - System.nanoTime();
      if (toWait < 0) {
        throw new TimeoutException();
      }
      try {
        future.get(toWait, TimeUnit.NANOSECONDS);
      }
      catch (CancellationException ignored) {
      }
    }
  }

  @NotNull
  @Contract(pure = true)
  public static Runnable underThreadNameRunnable(@NotNull final String name, @NotNull final Runnable runnable) {
    return () -> runUnderThreadName(name, runnable);
  }

  public static void runUnderThreadName(@NotNull final String name, @NotNull final Runnable runnable) {
    Thread currentThread = Thread.currentThread();
    String oldThreadName = currentThread.getName();
    if (name.equals(oldThreadName)) {
      runnable.run();
    }
    else {
      currentThread.setName(name);
      try {
        runnable.run();
      }
      finally {
        currentThread.setName(oldThreadName);
      }
    }
  }

  @NotNull
  public static Runnable once(@NotNull final Runnable delegate) {
    final AtomicBoolean done = new AtomicBoolean(false);
    return () -> {
      if (done.compareAndSet(false, true)) {
        delegate.run();
      }
    };
  }

  public static <T, E extends Throwable> T withLock(@NotNull Lock lock, @NotNull ThrowableComputable<T, E> runnable) throws E {
    lock.lock();
    try {
      return runnable.compute();
    }
    finally {
      lock.unlock();
    }
  }

  public static <E extends Throwable> void withLock(@NotNull Lock lock, @NotNull ThrowableRunnable<E> runnable) throws E {
    lock.lock();
    try {
      runnable.run();
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Complete the {@code task} and rethrow exceptions (wrapped in RuntimeException if necessary), if any.
   * Useful when the result of the Future is otherwise abandoned.
   */
  public static void manifestExceptionsIn(@NotNull Future<?> task) throws RuntimeException, Error {
    try {
      task.get();
    }
    catch (CancellationException | InterruptedException ignored) {
    }
    catch (ExecutionException e) {
      ExceptionUtil.rethrow(e.getCause());
    }
  }
}
