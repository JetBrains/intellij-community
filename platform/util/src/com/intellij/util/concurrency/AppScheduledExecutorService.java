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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcherManager;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.concurrent.*;

/**
 * A ThreadPoolExecutor which also implements {@link ScheduledExecutorService} by awaiting scheduled tasks in a separate thread
 * and then executing them in the owned ThreadPoolExecutor.
 * Unlike the existing {@link ScheduledThreadPoolExecutor}, this pool is unbounded.
 */
public class AppScheduledExecutorService extends SchedulingWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.ide.PooledThreadExecutor");
  static final String POOLED_THREAD_PREFIX = "ApplicationImpl pooled thread ";
  @NotNull private final String myName;
  private final LowMemoryWatcherManager myLowMemoryWatcherManager;
  private final MyThreadFactory myCountingThreadFactory;

  private static class Holder {
    private static final AppScheduledExecutorService INSTANCE = new AppScheduledExecutorService("Global instance");
  }

  @NotNull
  static ScheduledExecutorService getInstance() {
    return Holder.INSTANCE;
  }

  private static class MyThreadFactory extends CountingThreadFactory {
    private Consumer<? super Thread> newThreadListener;

    @NotNull
    @Override
    public Thread newThread(@NotNull final Runnable r) {
      Thread thread = new Thread(r, POOLED_THREAD_PREFIX + counter.incrementAndGet());

      thread.setPriority(Thread.NORM_PRIORITY - 1);

      Consumer<? super Thread> listener = newThreadListener;
      if (listener != null) {
        listener.consume(thread);
      }
      return thread;
    }

    void setNewThreadListener(@NotNull Consumer<? super Thread> threadListener) {
      if (newThreadListener != null) throw new IllegalStateException("Listener was already set: "+newThreadListener);
      newThreadListener = threadListener;
    }
  }

  AppScheduledExecutorService(@NotNull final String name) {
    super(new BackendThreadPoolExecutor(new MyThreadFactory()), new AppDelayQueue());
    myName = name;
    myCountingThreadFactory = (MyThreadFactory)((BackendThreadPoolExecutor)backendExecutorService).getThreadFactory();
    myLowMemoryWatcherManager = new LowMemoryWatcherManager(this);
  }

  public void setNewThreadListener(@NotNull Consumer<? super Thread> threadListener) {
    myCountingThreadFactory.setNewThreadListener(threadListener);
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return error();
  }

  @Override
  public void shutdown() {
    error();
  }

  static List<Runnable> error() {
    throw new IncorrectOperationException("You must not call this method on the global app pool");
  }

  @Override
  void doShutdown() {
    super.doShutdown();
    ((BackendThreadPoolExecutor)backendExecutorService).doShutdown();
  }

  @NotNull
  @Override
  List<Runnable> doShutdownNow() {
    return ContainerUtil.concat(super.doShutdownNow(), ((BackendThreadPoolExecutor)backendExecutorService).doShutdownNow());
  }

  public void shutdownAppScheduledExecutorService() {
    // LowMemoryWatcher starts background threads so stop it now to avoid RejectedExecutionException
    Disposer.dispose(myLowMemoryWatcherManager);
    delayQueue.shutdown(); // shutdown delay queue first to avoid rejected execution exceptions in Alarm
    doShutdown();
  }

  @NotNull
  @TestOnly
  public String statistics() {
    return myName + " threads created counter = " + myCountingThreadFactory.counter;
  }

  @TestOnly
  public String dumpQueue() {
    return delayQueue.toString();
  }

  public int getBackendPoolExecutorSize() {
    return ((BackendThreadPoolExecutor)backendExecutorService).getPoolSize();
  }
  void setBackendPoolCorePoolSize(int size) {
    ((BackendThreadPoolExecutor)backendExecutorService).doSetCorePoolSize(size);
  }
  int getBackendPoolCorePoolSize() {
    return ((BackendThreadPoolExecutor)backendExecutorService).getCorePoolSize();
  }

  private static class BackendThreadPoolExecutor extends ThreadPoolExecutor {
    BackendThreadPoolExecutor(@NotNull ThreadFactory factory) {
      super(1, Integer.MAX_VALUE, 1, TimeUnit.MINUTES, new SynchronousQueue<Runnable>(), factory);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("beforeExecute " + BoundedTaskExecutor.info(r) + " in " + t);
      }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("afterExecute  " + BoundedTaskExecutor.info(r) + " in " + Thread.currentThread());
      }

      if (t != null) {
        LOG.error("Worker exited due to exception", t);
      }
    }

    private void doShutdown() {
      super.shutdown();
    }

    @NotNull
    private List<Runnable> doShutdownNow() {
      return super.shutdownNow();
    }

    // stub out sensitive methods
    @Override
    public void shutdown() {
      error();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
      return error();
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
      error();
    }

    private void doSetCorePoolSize(int corePoolSize) {
      super.setCorePoolSize(corePoolSize);
    }

    @Override
    public void allowCoreThreadTimeOut(boolean value) {
      error();
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
      error();
    }

    @Override
    public void setKeepAliveTime(long time, TimeUnit unit) {
      error();
    }

    @Override
    public void setThreadFactory(ThreadFactory threadFactory) {
      error();
    }
  }
  @NotNull
  public Thread getPeriodicTasksThread() {
    return delayQueue.getThread();
  }
}
