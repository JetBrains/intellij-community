// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.LowMemoryWatcherManager;
import com.intellij.util.IncorrectOperationException;
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
 * @see AppExecutorUtil#getAppScheduledExecutorService()
 */
@ApiStatus.Internal
public final class AppScheduledExecutorService extends SchedulingWrapper {
  static final String POOLED_THREAD_PREFIX = "ApplicationImpl pooled thread ";
  @NotNull private final String myName;
  private final LowMemoryWatcherManager myLowMemoryWatcherManager;

  private static final class Holder {
    private static final AppScheduledExecutorService INSTANCE = new AppScheduledExecutorService("Global instance", 1, TimeUnit.MINUTES);
  }

  static @NotNull ScheduledExecutorService getInstance() {
    return Holder.INSTANCE;
  }

  AppScheduledExecutorService(@NotNull String name, long keepAliveTime, @NotNull TimeUnit unit) {
    super(new BackendThreadPoolExecutor(new MyThreadFactory(), keepAliveTime, unit), new AppDelayQueue());
    myName = name;
    myLowMemoryWatcherManager = new LowMemoryWatcherManager(this);
  }

  private MyThreadFactory getCountingThreadFactory() {
    return (MyThreadFactory)((BackendThreadPoolExecutor)backendExecutorService).getThreadFactory();
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

  public void setNewThreadListener(@NotNull BiConsumer<? super Thread, ? super Runnable> threadListener) {
    getCountingThreadFactory().setNewThreadListener(threadListener);
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return notAllowedMethodCall();
  }

  @Override
  public void shutdown() {
    notAllowedMethodCall();
  }

  static List<Runnable> notAllowedMethodCall() {
    throw new IncorrectOperationException("You must not call this method on the global app pool");
  }

  @Override
  void onDelayQueuePurgedOnShutdown() {
    ((BackendThreadPoolExecutor)backendExecutorService).superShutdown();
  }

  void shutdownAppScheduledExecutorService() {
    // LowMemoryWatcher starts background threads so stop it now to avoid RejectedExecutionException
    myLowMemoryWatcherManager.shutdown();
    doShutdown();
    delayQueue.shutdown(new SchedulingWrapper.MyScheduledFutureTask<Void>(()->{}, null, 0) {
        @Override
        boolean executeMeInBackendExecutor() {
          set(null);
          return false;
        }
      });
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    // let this task to bubble through the AppDelayQueue global queue to make sure there are no in-flight tasks leaked on stack of com.intellij.util.concurrency.AppDelayQueue.transferrerThread
    long deadline = System.nanoTime() + unit.toNanos(timeout);
    if (!delayQueue.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) {
      return false;
    }

    return super.awaitTermination(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
  }

  @NotNull
  @TestOnly
  public String statistics() {
    return myName + " threads created counter = " + getCountingThreadFactory().counter;
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
      executeRaw(capturePropagationAndCancellationContext(command));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
      return newTaskFor(Executors.callable(runnable, value));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
      return capturePropagationAndCancellationContext(callable);
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
      notAllowedMethodCall();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
      return notAllowedMethodCall();
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
      notAllowedMethodCall();
    }

    private void superSetCorePoolSize(int corePoolSize) {
      super.setCorePoolSize(corePoolSize);
    }

    @Override
    public void allowCoreThreadTimeOut(boolean value) {
      notAllowedMethodCall();
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
      notAllowedMethodCall();
    }

    @Override
    public void setKeepAliveTime(long time, TimeUnit unit) {
      notAllowedMethodCall();
    }

    void superSetKeepAliveTime(long time, TimeUnit unit) {
      super.setKeepAliveTime(time, unit);
    }

    @Override
    public void setThreadFactory(@NotNull ThreadFactory threadFactory) {
      notAllowedMethodCall();
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

  public static @NotNull Runnable capturePropagationAndCancellationContext(@NotNull Runnable command) {
    if (!propagateContextOrCancellation()) {
      return command;
    }
    return Propagation.capturePropagationAndCancellationContext(command);
  }

  public static <T> @NotNull FutureTask<T> capturePropagationAndCancellationContext(@NotNull Callable<T> callable) {
    if (!propagateContextOrCancellation()) {
      return new FutureTask<>(callable);
    }
    return Propagation.capturePropagationAndCancellationContext(callable);
  }
}
