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

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

public class AppExecutorUtil {
  /**
   * Returns application-wide instance of {@link ScheduledExecutorService} which is:
   * <ul>
   * <li>Unbounded. I.e. multiple {@link ScheduledExecutorService#schedule}(command, 0, TimeUnit.SECONDS) will lead to multiple executions of the {@code command} in parallel.</li>
   * <li>Backed by the application thread pool. I.e. every scheduled task will be executed in IDEA own thread pool. See {@link com.intellij.openapi.application.Application#executeOnPooledThread(Runnable)}</li>
   * <li>Non-shutdownable singleton. Any attempts to call {@link ExecutorService#shutdown()}, {@link ExecutorService#shutdownNow()} will be severely punished.</li>
   * <li>{@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)} is disallowed because it's bad for hibernation.
   *     Use {@link ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)} instead.</li>
   * </ul>
   * </ul>
   */
  @NotNull
  public static ScheduledExecutorService getAppScheduledExecutorService() {
    return AppScheduledExecutorService.getInstance();
  }

  /**
   * Application tread pool.
   * This pool is<ul>
   * <li>Unbounded.</li>
   * <li>Application-wide, always active, non-shutdownable singleton.</li>
   * </ul>
   * You can use this pool for long-running and/or IO-bound tasks.
   * @see com.intellij.openapi.application.Application#executeOnPooledThread(Runnable)
   */
  @NotNull
  public static ExecutorService getAppExecutorService() {
    return ((AppScheduledExecutorService)getAppScheduledExecutorService()).backendExecutorService;
  }

  /**
   * Returns {@link ScheduledExecutorService} which allows to {@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)} tasks later
   * and execute them in parallel in the application pool (see {@link #getAppExecutorService()} not more than at {@code maxThreads} at a time.
   */
  @NotNull
  public static ScheduledExecutorService createBoundedScheduledExecutorService(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String name, int maxThreads) {
    return new BoundedScheduledExecutorService(name, getAppExecutorService(), maxThreads);
  }

  /**
   * @return the bounded executor (executor which runs no more than {@code maxThreads} tasks simultaneously) backed by the application pool
   *         (i.e. all tasks are run in the {@link #getAppExecutorService()} global thread pool).
   * @see #getAppExecutorService()
   */
  @NotNull
  public static ExecutorService createBoundedApplicationPoolExecutor(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String name, int maxThreads) {
    return createBoundedApplicationPoolExecutor(name, getAppExecutorService(), maxThreads);
  }

  /**
   * @return the bounded executor (executor which runs no more than {@code maxThreads} tasks simultaneously) backed by the {@code backendExecutor}
   */
  @NotNull
  public static ExecutorService createBoundedApplicationPoolExecutor(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String name, @NotNull Executor backendExecutor, int maxThreads) {
    return new BoundedTaskExecutor(name, backendExecutor, maxThreads);
  }
  /**
   * @return the bounded executor (executor which runs no more than {@code maxThreads} tasks simultaneously) backed by the {@code backendExecutor}
   * which will shutdown itself when {@code parentDisposable} gets disposed.
   */
  @NotNull
  public static BoundedTaskExecutor createBoundedApplicationPoolExecutor(@NotNull @Nls(capitalization = Nls.Capitalization.Title) String name, @NotNull Executor backendExecutor, int maxThreads, @NotNull
                                                                     Disposable parentDisposable) {
    return new BoundedTaskExecutor(name, backendExecutor, maxThreads, parentDisposable);
  }
}
