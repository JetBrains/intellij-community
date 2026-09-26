// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * Prefer <a href="https://plugins.jetbrains.com/docs/intellij/kotlin-coroutines.html">Kotlin coroutines</a> in new code.
 * A coroutine launched on {@code Dispatchers.IO} replaces a task submitted to the application pool, and
 * {@code Dispatchers.IO.limitedParallelism(n)} replaces a bounded executor created here. Structured concurrency then ties
 * the work to a scope, so cancellation and shutdown stop being the caller's bookkeeping.
 * </p>
 */
public final class AppExecutorUtil {
  /**
   * Returns application-wide instance of {@link ScheduledExecutorService} which is:
   * <ul>
   * <li>Unbounded. I.e. multiple {@code ScheduledExecutorService#schedule(command, 0, TimeUnit.SECONDS)} will lead to multiple executions of the {@code command} in parallel.</li>
   * <li>Backed by the application thread pool. I.e. every scheduled task will be executed in the IDE's own thread pool. See {@link com.intellij.openapi.application.Application#executeOnPooledThread(Runnable)}</li>
   * <li>Non-shutdownable singleton. Any attempts to call {@link ExecutorService#shutdown()}, {@link ExecutorService#shutdownNow()} will be severely punished.</li>
   * <li>{@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)} is disallowed because it's bad for hibernation.
   *     Use {@link ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)} instead.</li>
   * </ul>
   */
  public static @NotNull ScheduledExecutorService getAppScheduledExecutorService() {
    return AppScheduledExecutorService.getInstance();
  }

  /**
   * Application thread pool.
   * This pool is<ul>
   * <li>Unbounded.</li>
   * <li>Application-wide, always active, non-shutdownable singleton.</li>
   * </ul>
   * You can use this pool for long-running and/or IO-bound tasks.
   * @see com.intellij.openapi.application.Application#executeOnPooledThread(Runnable)
   */
  public static @NotNull ExecutorService getAppExecutorService() {
    return ((AppScheduledExecutorService)getAppScheduledExecutorService()).backendExecutorService;
  }

  /**
   * Returns {@link ScheduledExecutorService} which allows to {@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)} tasks later
   * and execute them in parallel in the application pool (see {@link #getAppExecutorService()}) not more than at {@code maxThreads} at a time.
   * The created pool doesn't keep queued but not yet executed delayed tasks on shutdown,
   * which equivalent to having both {@code ExecuteExistingDelayedTasksAfterShutdownPolicy} and {@code ContinueExistingPeriodicTasksAfterShutdownPolicy} policies to false.
   * See {@link java.util.concurrent.ScheduledThreadPoolExecutor#getExecuteExistingDelayedTasksAfterShutdownPolicy()} and {@link ScheduledThreadPoolExecutor#getContinueExistingPeriodicTasksAfterShutdownPolicy()} for details.
   * @param name is used to generate thread name which will be shown in thread dumps, so it should be human-readable and use Title Capitalization.
   *             If more than one task can run simultaneously, a {@code "-<index>"} suffix is appended to the thread name
   *             to make one thread of the pool distinguishable from another.
   *
   */
  public static @NotNull ScheduledExecutorService createBoundedScheduledExecutorService(@NotNull @NonNls String name, int maxThreads) {
    return new BoundedScheduledExecutorService(name, getAppExecutorService(), maxThreads);
  }

  /**
   * <h3>Obsolescence notice</h3>
   * <p>
   * Use {@code Dispatchers.IO.limitedParallelism(maxThreads)} instead, stored in a field and shared by every caller that
   * belongs to the same limit. Creating one per batch of tasks multiplies the concurrency it was supposed to cap, because
   * each instance limits only itself while all of them draw from the same underlying pool — the same trap this method has.
   * See the notice on {@link AppExecutorUtil}.
   * </p>
   * <hr>
   *
   * @return the bounded executor (executor which runs no more than {@code maxThreads} tasks simultaneously) backed by the application pool
   *         (i.e., all tasks are run in the {@link #getAppExecutorService()} global thread pool).
   * @param name is used to generate thread name which will be shown in thread dumps, so it should be human-readable and use Title Capitalization.
   *             If more than one task can run simultaneously, a {@code "-<index>"} suffix is appended to the thread name
   *             to make one thread of the pool distinguishable from another.
   * @see #getAppExecutorService()
   */
  @ApiStatus.Obsolete
  public static @NotNull ExecutorService createBoundedApplicationPoolExecutor(@NotNull @NonNls String name, int maxThreads) {
    return createBoundedApplicationPoolExecutor(name, getAppExecutorService(), maxThreads);
  }

  /**
   * See <b>obsolescence notice</b> on {@link #createBoundedApplicationPoolExecutor(String, int)}.
   */
  @ApiStatus.Internal
  @ApiStatus.Obsolete
  public static @NotNull ExecutorService createBoundedApplicationPoolExecutor(@NotNull @NonNls String name, int maxThreads, boolean changeThreadName) {
    return new BoundedTaskExecutor(name, getAppExecutorService(), maxThreads, changeThreadName);
  }

  /**
   * See <b>obsolescence notice</b> on {@link #createBoundedApplicationPoolExecutor(String, int)}.
   * <hr>
   *
   * @param name is used to generate thread name which will be shown in thread dumps, so it should be human-readable and use Title Capitalization.
   *             If more than one task can run simultaneously, a {@code "-<index>"} suffix is appended to the thread name
   *             to make one thread of the pool distinguishable from another.
   * @return the bounded executor (executor which runs no more than {@code maxThreads} tasks simultaneously) backed by the {@code backendExecutor}
   */
  @ApiStatus.Obsolete
  public static @NotNull ExecutorService createBoundedApplicationPoolExecutor(@NotNull @NonNls String name, @NotNull Executor backendExecutor, int maxThreads) {
    return new BoundedTaskExecutor(name, backendExecutor, maxThreads, true);
  }
  /**
   * See <b>obsolescence notice</b> on {@link #createBoundedApplicationPoolExecutor(String, int)}.
   * <hr>
   *
   * @param name is used to generate thread name which will be shown in thread dumps, so it should be human-readable and use Title Capitalization.
   *             If more than one task can run simultaneously, a {@code "-<index>"} suffix is appended to the thread name
   *             to make one thread of the pool distinguishable from another.
   * @return the bounded executor (executor which runs no more than {@code maxThreads} tasks simultaneously) backed by the {@code backendExecutor}
   * which will shut down itself when {@code parentDisposable} gets disposed.
   */
  @ApiStatus.Obsolete
  public static @NotNull ExecutorService createBoundedApplicationPoolExecutor(@NotNull @NonNls String name,
                                                                     @NotNull Executor backendExecutor,
                                                                     int maxThreads,
                                                                     @NotNull Disposable parentDisposable) {
    BoundedTaskExecutor executor = new BoundedTaskExecutor(name, backendExecutor, maxThreads, true);
    Disposer.register(parentDisposable, () -> executor.shutdownNow());
    return executor;
  }

  /**
   * @param name is used to generate thread name which will be shown in thread dumps, so it should be human-readable and use Title Capitalization.
   *             If more than one task can run simultaneously, a {@code "-<index>"} suffix is appended to the thread name
   *             to make one thread of the pool distinguishable from another.
   * @return the bounded executor (executor which runs no more than {@code maxThreads} tasks simultaneously) backed by the {@code backendExecutor}.
   * Tasks are prioritized according to {@code comparator}.
   */
  public static @NotNull ExecutorService createCustomPriorityQueueBoundedApplicationPoolExecutor(@NotNull @NonNls String name,
                                                                                        @NotNull Executor backendExecutor,
                                                                                        int maxThreads,
                                                                                        @NotNull Comparator<? super Runnable> comparator) {
    return new BoundedTaskExecutor(name, backendExecutor, maxThreads, true, new PriorityBlockingQueue<>(11, comparator));
  }

  @ApiStatus.Internal
  public static void shutdownApplicationScheduledExecutorService() {
    ((AppScheduledExecutorService)AppScheduledExecutorService.getInstance()).shutdownAppScheduledExecutorService();
  }

  @ApiStatus.Internal
  public static boolean propagateContext() {
    return Propagation.isPropagateThreadContext();
  }
}
