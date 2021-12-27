// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.LowMemoryWatcherManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import static com.intellij.util.concurrency.AppExecutorUtil.propagateContextOrCancellation;

/**
 * A ThreadPoolExecutor which also implements {@link ScheduledExecutorService} by awaiting scheduled tasks in a separate thread
 * and then executing them in the owned ThreadPoolExecutor.
 * Unlike the existing {@link ScheduledThreadPoolExecutor}, this pool is unbounded.
 */
@ApiStatus.Internal
public final class AppScheduledExecutorService extends SchedulingWrapper {
  static final String POOLED_THREAD_PREFIX = "ApplicationImpl pooled thread ";
  @NotNull private final String myName;
  private final LowMemoryWatcherManager myLowMemoryWatcherManager;
  private final MyThreadFactory myCountingThreadFactory;

  private static final class Holder {
    private static final AppScheduledExecutorService INSTANCE = new AppScheduledExecutorService("Global instance", 1, TimeUnit.MINUTES);
  }

  static @NotNull ScheduledExecutorService getInstance() {
    return Holder.INSTANCE;
  }

  private static final class MyThreadFactory extends CountingThreadFactory {
    private BiConsumer<? super Thread, ? super Runnable> newThreadListener;
    private final ThreadFactory myThreadFactory = Executors.privilegedThreadFactory();

    @NotNull
    @Override
    public Thread newThread(@NotNull final Runnable r) {
      Thread thread = myThreadFactory.newThread(r);
      thread.setName(POOLED_THREAD_PREFIX + counter.incrementAndGet());

      thread.setPriority(Thread.NORM_PRIORITY - 1);

      BiConsumer<? super Thread, ? super Runnable> listener = newThreadListener;
      if (listener != null) {
        listener.accept(thread, r);
      }
      return thread;
    }

    void setNewThreadListener(@NotNull BiConsumer<? super Thread, ? super Runnable> threadListener) {
      if (newThreadListener != null) throw new IllegalStateException("Listener was already set: " + newThreadListener);
      newThreadListener = threadListener;
    }
  }

  AppScheduledExecutorService(@NotNull String name, long keepAliveTime, @NotNull TimeUnit unit) {
    super(new BackendThreadPoolExecutor(new MyThreadFactory(), keepAliveTime, unit), new AppDelayQueue());
    myName = name;
    myCountingThreadFactory = (MyThreadFactory)((BackendThreadPoolExecutor)backendExecutorService).getThreadFactory();
    myLowMemoryWatcherManager = new LowMemoryWatcherManager(this);
  }

  public void setNewThreadListener(@NotNull BiConsumer<? super Thread, ? super Runnable> threadListener) {
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
    ((BackendThreadPoolExecutor)backendExecutorService).superShutdown();
  }

  @NotNull
  @Override
  List<Runnable> doShutdownNow() {
    return ContainerUtil.concat(super.doShutdownNow(), ((BackendThreadPoolExecutor)backendExecutorService).superShutdownNow());
  }

  void shutdownAppScheduledExecutorService() {
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

  @TestOnly
  void setBackendPoolCorePoolSize(int size) {
    ((BackendThreadPoolExecutor)backendExecutorService).superSetCorePoolSize(size);
  }

  static class BackendThreadPoolExecutor extends ThreadPoolExecutor implements ContextPropagatingExecutor {

    BackendThreadPoolExecutor(@NotNull ThreadFactory factory,
                              long keepAliveTime,
                              @NotNull TimeUnit unit) {
      super(1, Integer.MAX_VALUE, keepAliveTime, unit, new SynchronousQueue<>(), factory);
    }

    @Override
    public void executeRaw(@NotNull Runnable command) {
      super.execute(command);
    }

    @Override
    public void execute(@NotNull Runnable command) {
      executeRaw(handleCommand(command));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
      return newTaskFor(Executors.callable(runnable, value));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
      return handleTask(callable);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      if (t != null && !(t instanceof ProcessCanceledException)) {
        Logger.getInstance(SchedulingWrapper.class).error("Worker exited due to exception", t);
      }
    }

    private void superShutdown() {
      super.shutdown();
    }

    @NotNull
    private List<Runnable> superShutdownNow() {
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

    private void superSetCorePoolSize(int corePoolSize) {
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

    void superSetKeepAliveTime(long time, TimeUnit unit) {
      super.setKeepAliveTime(time, unit);
    }

    @Override
    public void setThreadFactory(ThreadFactory threadFactory) {
      error();
    }
  }

  public static @NotNull Thread getPeriodicTasksThread() {
    return Holder.INSTANCE.delayQueue.getThread();
  }

  @TestOnly
  void waitForLowMemoryWatcherManagerInit(int timeout, @NotNull TimeUnit unit)
    throws InterruptedException, ExecutionException, TimeoutException {
    myLowMemoryWatcherManager.waitForInitComplete(timeout, unit);
  }

  static @NotNull Runnable handleCommand(@NotNull Runnable command) {
    if (command instanceof FutureTask) {
      // FutureTask.callable should handle propagation/cancellation.
      // Known implementations:
      // - CancellationFutureTask is created in newTaskFor;
      // - java.util.concurrent.ExecutorCompletionService$QueueingFuture (wraps CancellationFutureTask) is created in invokeAny
      // - com.intellij.util.concurrency.SchedulingWrapper$MyScheduledFutureTask;
      return command;
    }
    if (!propagateContextOrCancellation()) {
      return command;
    }
    return Propagation.handleCommand(command);
  }

  static <T> @NotNull FutureTask<T> handleTask(Callable<T> callable) {
    if (!propagateContextOrCancellation()) {
      return new FutureTask<>(callable);
    }
    return Propagation.handleTask(callable);
  }
}
