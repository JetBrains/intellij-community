// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MergingTaskQueue<T extends MergeableQueueTask<T>> {
  private static final Logger LOG = Logger.getInstance(MergingTaskQueue.class);

  private final Object myLock = new Object();
  //does not include a running task
  private final List<@NotNull T> myTasksQueue = new ArrayList<>();

  //includes running tasks too
  private final Map<T, ProgressIndicatorBase> myProgresses = new HashMap<>();

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
    }

    cancelIndicatorSafe(indicatorsQueue);
    disposeSafe(disposeQueue);
  }

  public void cancelAllTasks() {
    List<ProgressIndicatorEx> tasks;
    synchronized (myLock) {
      tasks = new ArrayList<>(myProgresses.values());
    }

    for (ProgressIndicatorEx indicator : tasks) {
      indicator.cancel();
    }
  }

  public void cancelTask(@NotNull DumbModeTask task) {
    ProgressIndicatorEx indicator;
    synchronized (myLock) {
      indicator = myProgresses.get(task);
    }

    if (indicator != null) {
      indicator.cancel();
    }
  }

  public void addTask(@NotNull T task) {
    List<T> disposeQueue = new ArrayList<>(1);

    synchronized (myLock) {
      for (int i = myTasksQueue.size() - 1; i >= 0; i--) {
        T oldTask = myTasksQueue.get(i);
        ProgressIndicatorBase indicator = myProgresses.get(oldTask);
        //dispose cancelled tasks
        if (indicator == null || indicator.isCanceled()) {
          myTasksQueue.remove(i);
          disposeQueue.add(oldTask);
          continue;
        }
        T mergedTask = task.tryMergeWith(oldTask);
        if (mergedTask != null) {
          LOG.debug("Merged " + task + " with " + oldTask);
          task = mergedTask;
          myTasksQueue.remove(i);
          disposeQueue.add(oldTask);
          break;
        }
      }

      //register the new task last, preserving FIFO order
      T taskToAdd = task;
      myTasksQueue.add(taskToAdd);
      ProgressIndicatorBase progress = new ProgressIndicatorBase();
      myProgresses.put(taskToAdd, progress);
      Disposer.register(taskToAdd, () -> {
        //a removed progress means the task would be ignored on queue processing
        synchronized (myLock) {
          myProgresses.remove(taskToAdd);
        }
        progress.cancel();
      });
    }

    disposeSafe(disposeQueue);
  }

  @Nullable
  public MergingTaskQueue.QueuedTask<T> extractNextTask() {
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

          return wrapTask(task, indicator);
        }
      }
    }
    finally {
      disposeSafe(disposeQueue);
    }
  }

  protected MergingTaskQueue.QueuedTask<T> wrapTask(T task, ProgressIndicatorBase indicator) {
    return new QueuedTask<>(task, indicator);
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
        LOG.warn("Failed to dispose DumbModeTask: " + t.getMessage(), t);
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
        LOG.warn("Failed to cancel DumbModeTask indicator: " + t.getMessage(), t);
      }
    }
  }

  public static class QueuedTask<T extends MergeableQueueTask<T>> implements AutoCloseable {
    private final T myTask;
    private final ProgressIndicatorEx myIndicator;

    QueuedTask(@NotNull T task, @NotNull ProgressIndicatorEx progress) {
      myTask = task;
      myIndicator = progress;
    }

    @Override
    public void close() {
      Disposer.dispose(myTask);
    }

    @NotNull
    public ProgressIndicatorEx getIndicator() {
      return myIndicator;
    }

    public void executeTask() {
      executeTask(null);
    }

    public void executeTask(@Nullable ProgressIndicator customIndicator) {
      //this is the cancellation check
      myIndicator.checkCanceled();
      myIndicator.setIndeterminate(true);

      if (customIndicator == null) {
        customIndicator = myIndicator;
      }
      else {
        customIndicator.checkCanceled();
      }

      beforeTask();
      myTask.perform(customIndicator);
    }

    String getInfoString() {
      return String.valueOf(myTask);
    }

    protected T getTask() {
      return myTask;
    }

    public void beforeTask() {

    }
  }
}
