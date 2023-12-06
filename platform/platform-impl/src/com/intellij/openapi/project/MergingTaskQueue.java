// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.concurrency.ThreadContext;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.ChildContext;
import com.intellij.util.concurrency.Propagation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class MergingTaskQueue<T extends MergeableQueueTask<T>> {
  public static final class SubmissionReceipt {
    private final long submittedTaskCount;

    private SubmissionReceipt(long submittedTaskCount) {
      this.submittedTaskCount = submittedTaskCount;
    }

    public boolean isAfter(@NotNull SubmissionReceipt other) {
      return submittedTaskCount > other.submittedTaskCount;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      return (submittedTaskCount == ((SubmissionReceipt)o).submittedTaskCount);
    }

    @Override
    public int hashCode() {
      return Long.hashCode(submittedTaskCount);
    }

    @Override
    public String toString() {
      return "SubmissionReceipt{" + submittedTaskCount + '}';
    }
  }

  private static final Logger LOG = Logger.getInstance(MergingTaskQueue.class);

  private final Object myLock = new Object();
  //does not include a running task
  private final List<@NotNull T> myTasksQueue = new ArrayList<>();

  //includes running tasks too
  private final Map<T, ProgressIndicatorBase> myProgresses = new HashMap<>();
  private final AtomicLong mySubmittedTasksCount = new AtomicLong();

  /**
   * A thread context for each task
   */
  private final Map<T, ChildContext> myThreadContexts = new HashMap<>();

  /**
   * Disposes tasks, cancel underlying progress indicators, clears tasks queue
   */
  public void disposePendingTasks() {
    List<T> disposeQueue;
    List<ProgressIndicatorEx> indicatorsQueue;
    synchronized (myLock) {
      //running task is not included, we must not dispose it if it is (probably) running
      disposeQueue = new ArrayList<>(myTasksQueue);
      // the keys also include a running task(s)
      indicatorsQueue = new ArrayList<>(myProgresses.values());

      myTasksQueue.clear();
      myProgresses.clear();
      myThreadContexts.clear();
    }

    cancelIndicatorSafe(indicatorsQueue);
    disposeSafe(disposeQueue);
  }

  // This method is not public because it cannot cancel tasks paused by ProgressSuspender.
  // Use methods from appropriate executor instead (e.g. MergingQueueGuiExecutor#cancelAllTasks)
  void cancelAllTasks() {
    List<ProgressIndicatorEx> tasks;
    synchronized (myLock) {
      tasks = new ArrayList<>(myProgresses.values());
    }

    for (ProgressIndicatorEx indicator : tasks) {
      indicator.cancel();
    }
  }

  public void cancelTask(@NotNull T task) {
    ProgressIndicatorEx indicator;
    synchronized (myLock) {
      indicator = myProgresses.get(task);
    }

    if (indicator != null) {
      indicator.cancel();
    }
  }

  /**
   * Adds a task to the queue. Added task can be merged with one of the existing tasks.
   *
   * @param task to add
   * @return receipt that later can be used to handle concurrent operations. Note that addTask may produce duplicate receipt if new task
   * does not modify queue state (e.g. when new task is merged into one of the existing tasks)
   */
  public SubmissionReceipt addTask(@NotNull T task) {
    List<T> disposeQueue = new ArrayList<>(1);
    T newTask = task;
    SubmissionReceipt receipt;

    var childContext = AppExecutorUtil.propagateContextOrCancellation() ? Propagation.createChildContext() : null;
    CoroutineContext currentContext = childContext == null ? null : childContext.getContext();
    Job job = childContext == null ? null : childContext.getJob();

    synchronized (myLock) {
      for (int i = myTasksQueue.size() - 1; i >= 0; i--) {
        T oldTask = myTasksQueue.get(i);
        ProgressIndicatorBase indicator = myProgresses.get(oldTask);
        ChildContext otherChildContext = myThreadContexts.get(oldTask);
        CoroutineContext otherContext = otherChildContext == null ? null : otherChildContext.getContext();
        //dispose cancelled tasks
        if (indicator == null || indicator.isCanceled()) {
          myTasksQueue.remove(i);
          disposeQueue.add(oldTask);
          continue;
        }

        // note that parent may know nothing about children, so the following may happen (just like in case with `equals` with inheritance):
        //     class Parent; class Child extends Parent
        //     parent.tryMergeWith(child) != child.tryMergeWith(parent)
        // At the moment we prevent accidental errors by forcing tasks' class equality.
        // More permissive strategy (that we don't apply) would be to check "isAssignableFrom" and always use `child.tryMergeWith`
        if (task.getClass() != oldTask.getClass()) {
          continue;
        }

        T mergedTask = task.tryMergeWith(oldTask);
        boolean contextsEqual;
        if (AppExecutorUtil.propagateContextOrCancellation()) {
          if ((currentContext == null) != (otherContext == null)) {
            contextsEqual = false;
          } else {
            contextsEqual = currentContext == null || ThreadContext.getContextSkeleton(currentContext).equals(ThreadContext.getContextSkeleton(otherContext));
          }
        } else {
          contextsEqual = true;
        }
        if (mergedTask == oldTask && contextsEqual) {
          // new task completely absorbed by the old task which means that we don't need to modify the queue
          newTask = null;
          if (job != null) {
            job.cancel(null);
          }
          disposeQueue.add(task);
          break;
        }

        if (mergedTask != null) {
          LOG.debug("Merged " + task + " with " + oldTask);
          newTask = mergedTask;
          myTasksQueue.remove(i);
          disposeQueue.add(oldTask);
          break;
        }
      }

      //register the new task last, preserving FIFO order
      T taskToAdd = newTask;
      if (taskToAdd != null) {
        myTasksQueue.add(taskToAdd);
        mySubmittedTasksCount.incrementAndGet();
        ProgressIndicatorBase progress = new ProgressIndicatorBase();
        myProgresses.put(taskToAdd, progress);
        myThreadContexts.put(taskToAdd, childContext);
        Disposer.register(taskToAdd, () -> {
          //a removed progress means the task would be ignored on queue processing
          synchronized (myLock) {
            myProgresses.remove(taskToAdd);
            myThreadContexts.remove(taskToAdd);
            if (job != null) {
              job.cancel(null);
            }
          }
          progress.cancel();
        });
      }

      receipt = new SubmissionReceipt(mySubmittedTasksCount.get());
    }

    disposeSafe(disposeQueue);
    return receipt;
  }

  /**
   * @return receipt that equals to the latest receipt returned by {@linkplain #addTask(MergeableQueueTask)}
   */
  public SubmissionReceipt getLatestSubmissionReceipt() {
    synchronized (myLock) {
      // don't be fooled by AtomicLong. We need "synchronized" to make sure that mySubmittedTaskCount and myTasksQueue are changing together
      return new SubmissionReceipt(mySubmittedTasksCount.get());
    }
  }

  public @Nullable MergingTaskQueue.QueuedTask<T> extractNextTask() {
    List<Disposable> disposeQueue = new ArrayList<>(1);

    try {
      synchronized (myLock) {
        while (true) {
          if (myTasksQueue.isEmpty()) return null;

          T task = myTasksQueue.remove(0);

          ProgressIndicatorBase indicator = myProgresses.get(task);
          //a disposed task is just ignored here
          if (indicator == null || indicator.isCanceled()) {
            disposeQueue.add(task);
            continue;
          }
          var childContext = myThreadContexts.get(task);
          return wrapTask(task, indicator, childContext);
        }
      }
    }
    finally {
      disposeSafe(disposeQueue);
    }
  }

  protected MergingTaskQueue.QueuedTask<T> wrapTask(T task, ProgressIndicatorBase indicator, @Nullable ChildContext childContext) {
    return new QueuedTask<>(task, indicator, childContext);
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myTasksQueue.isEmpty();
    }
  }

  private static void disposeSafe(@NotNull Collection<? extends Disposable> tasks) {
    for (Disposable task : tasks) {
      disposeSafe(task);
    }
  }

  private static void disposeSafe(@NotNull Disposable task) {
    try {
      if (Disposer.isDisposed(task)) return;

      Disposer.dispose(task);
    }
    catch (Throwable t) {
      if (!(t instanceof ControlFlowException)) {
        LOG.warn("Failed to dispose task: " + t.getMessage(), t);
      }
    }
  }

  private static void cancelIndicatorSafe(@NotNull List<? extends ProgressIndicatorEx> indicators) {
    for (ProgressIndicatorEx indicator : indicators) {
      cancelIndicatorSafe(indicator);
    }
  }

  private static void cancelIndicatorSafe(@NotNull ProgressIndicatorEx indicator) {
    try {
      indicator.cancel();
    }
    catch (Throwable t) {
      if (!(t instanceof ControlFlowException)) {
        LOG.warn("Failed to cancel task indicator: " + t.getMessage(), t);
      }
    }
  }

  public static class QueuedTask<T extends MergeableQueueTask<T>> implements AutoCloseable {
    private final T myTask;
    private final ProgressIndicatorEx myIndicator;
    private final @Nullable ChildContext myChildContext;

    QueuedTask(@NotNull T task, @NotNull ProgressIndicatorEx progress, @Nullable ChildContext childContext) {
      myTask = task;
      myIndicator = progress;
      myChildContext = childContext;
    }

    @Override
    public void close() {
      Disposer.dispose(myTask);
    }

    public @NotNull ProgressIndicatorEx getIndicator() {
      return myIndicator;
    }

    public void executeTask() {
      executeTask(null);
    }

    public void executeTask(@Nullable ProgressIndicator customIndicator) {
      //this is the cancellation check
      myIndicator.checkCanceled();
      myIndicator.setIndeterminate(true);

      ProgressIndicator indicator = customIndicator == null ? myIndicator : customIndicator;
      indicator.checkCanceled();

      beforeTask();
      if (AppExecutorUtil.propagateContextOrCancellation() && myChildContext != null) {
        try (AccessToken ignored = ThreadContext.installThreadContext(myChildContext.getContext(), true)) {
          myChildContext.runAsCoroutine(() -> myTask.perform(indicator));
        }
      }
      else {
        myTask.perform(indicator);
      }
    }

    String getInfoString() {
      return String.valueOf(myTask);
    }

    protected T getTask() {
      return myTask;
    }

    /**
     * Override in children classes to report this task to FUS.
     * <p>
     * Override {@link MergingQueueGuiExecutor#runSingleTask(QueuedTask, StructuredIdeActivity)} or
     * {@link MergingQueueGuiExecutor#processTasksWithProgress(ProgressSuspender, ProgressIndicator, StructuredIdeActivity)} to start
     * FUS activity.
     */
    void registerStageStarted(@NotNull StructuredIdeActivity activity) {
    }

    public void beforeTask() {

    }
  }
}
