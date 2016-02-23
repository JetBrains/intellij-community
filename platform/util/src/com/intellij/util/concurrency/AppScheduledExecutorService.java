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
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A ThreadPoolExecutor which also implements {@link ScheduledExecutorService} by awaiting scheduled tasks in a separate thread
 * and then executing them in the owned ThreadPoolExecutor.
 * Unlike the existing {@link ScheduledThreadPoolExecutor}, this pool is unbounded.
 */
public class AppScheduledExecutorService extends SchedulingWrapper {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.ide.PooledThreadExecutor");
  private Consumer<Thread> newThreadListener;

  private static class Holder {
    private static final AppScheduledExecutorService INSTANCE = new AppScheduledExecutorService();
  }

  @NotNull
  static ScheduledExecutorService getInstance() {
    return Holder.INSTANCE;
  }

  AppScheduledExecutorService() {
    super(new BackendThreadPoolExecutor(), new AppDelayQueue());
    ((BackendThreadPoolExecutor)backendExecutorService).doSetThreadFactory(new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger();
      @NotNull
      @Override
      public Thread newThread(@NotNull final Runnable r) {
        Thread thread = new Thread(r, "ApplicationImpl pooled thread " + counter.incrementAndGet());

        thread.setPriority(Thread.NORM_PRIORITY - 1);

        Consumer<Thread> listener = newThreadListener;
        if (listener != null) {
          listener.consume(thread);
        }
        return thread;
      }
    });
  }

  public void setNewThreadListener(@NotNull Consumer<Thread> threadListener) {
    if (newThreadListener != null) throw new IllegalStateException("Listener was already set: "+newThreadListener);
    newThreadListener = threadListener;
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

  private static List<Runnable> error() {
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
    delayQueue.shutdown(); // shutdown delay queue first to avoid rejected execution exceptions in Alarm
    doShutdown();
  }

  public int getBackendPoolExecutorSize() {
    return ((ThreadPoolExecutor)backendExecutorService).getPoolSize();
  }

  private static class BackendThreadPoolExecutor extends ThreadPoolExecutor {
    BackendThreadPoolExecutor() {
      super(3, Integer.MAX_VALUE, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("Running " + BoundedTaskExecutor.info(r) + " in thread@" + System.identityHashCode(t));
      }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      if (t != null) {
        LOG.error("Worker exited due to exception", t);
      }
    }

    private void doShutdown() {
      super.shutdown();
    }
    private List<Runnable> doShutdownNow() {
      return super.shutdownNow();
    }

    private void doSetThreadFactory(ThreadFactory threadFactory) {
      super.setThreadFactory(threadFactory);
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
}
