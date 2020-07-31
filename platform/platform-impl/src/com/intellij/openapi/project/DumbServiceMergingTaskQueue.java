// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.internal.statistic.IdeActivity;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DumbServiceMergingTaskQueue {
  private static final Logger LOG = Logger.getInstance(DumbServiceMergingTaskQueue.class);

  private final Object myLock = new Object();
  //does not include a running task
  private final Map<Object, DumbModeTask> myTasksQueue = new LinkedHashMap<>();

  //includes running tasks too
  private final Map<DumbModeTask, ProgressIndicatorBase> myProgresses = new HashMap<>();

  /**
   * Disposes tasks, cancel underlying progress indicators, clears tasks queue
   */
  void disposePendingTasks() {
    List<DumbModeTask> disposeQueue;
    List<ProgressIndicatorEx> indicatorsQueue;
    synchronized (myLock) {
      //running task is not included, we must not dispose it if it is (probably) running
      disposeQueue = new ArrayList<>(myTasksQueue.values());
      // the keys also include a running task(s)
      indicatorsQueue = new ArrayList<>(myProgresses.values());

      myTasksQueue.clear();
      myProgresses.clear();
    }

    cancelIndicatorSafe(indicatorsQueue);
    disposeSafe(disposeQueue);
  }

  void cancelAllTasks() {
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

  public void addTask(@NotNull DumbModeTask task) {
    DumbModeTask olderTask;

    synchronized (myLock) {
      Object key = task.getEquivalenceObject();
      //remove key to preserve FIFO order
      olderTask = myTasksQueue.remove(key);

      //register the new task last
      myTasksQueue.put(key, task);

      ProgressIndicatorBase progress = new ProgressIndicatorBase();
      myProgresses.put(task, progress);
      Disposer.register(task, () -> {
        //a removed progress means the task would be ignored on queue processing
        synchronized (myLock) {
          myProgresses.remove(task);
        }
        progress.cancel();
      });
    }

    if (olderTask != null) {
      Disposer.dispose(olderTask);
    }
  }

  @Nullable
  public QueuedDumbModeTask extractNextTask() {
    List<DumbModeTask> disposeQueue = new ArrayList<>(1);

    try {
      synchronized (myLock) {
        while (true) {
          if (myTasksQueue.isEmpty()) return null;

          Object key = myTasksQueue.keySet().iterator().next();
          DumbModeTask task = myTasksQueue.remove(key);
          if (task == null) continue;

          ProgressIndicatorBase indicator = myProgresses.get(task);
          //a disposed task is just ignored here
          if (indicator == null || indicator.isCanceled()) {
            disposeQueue.add(task);
            continue;
          }

          return new QueuedDumbModeTask(task, indicator);
        }
      }
    } finally {
      disposeSafe(disposeQueue);
    }
  }

  private static void disposeSafe(@NotNull Collection<DumbModeTask> tasks) {
    for (DumbModeTask task : tasks) {
      disposeSafe(task);
    }
  }

  private static void disposeSafe(@NotNull DumbModeTask task) {
    try {
      if (Disposer.isDisposed(task)) return;

      Disposer.dispose(task);
    } catch (Throwable t) {
      if (!(t instanceof ControlFlowException)) {
        LOG.warn("Failed to dispose DumbModeTask: " + t.getMessage(), t);
      }
    }
  }

  private static void cancelIndicatorSafe(@NotNull List<ProgressIndicatorEx> indicators) {
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

  static class QueuedDumbModeTask implements AutoCloseable {
    private final DumbModeTask myTask;
    private final ProgressIndicatorEx myIndicator;

    QueuedDumbModeTask(@NotNull DumbModeTask task,
                       @NotNull ProgressIndicatorEx progress) {
      myTask = task;
      myIndicator = progress;
    }

    @Override
    public void close() {
      Disposer.dispose(myTask);
    }

    @NotNull
    ProgressIndicatorEx getIndicator() {
      return myIndicator;
    }

    void executeTask() {
      executeTask(null);
    }

    void executeTask(@Nullable ProgressIndicator customIndicator) {
      //this is the cancellation check
      myIndicator.checkCanceled();
      myIndicator.setIndeterminate(true);

      if (customIndicator == null) {
        customIndicator = myIndicator;
      } else {
        customIndicator.checkCanceled();
        //ProgressIndicator customIndicatorFinal = customIndicator;
        //new ProgressIndicatorListenerAdapter() {
        //  @Override
        //  public void cancelled() {
        //    customIndicatorFinal.cancel();
        //  }
        //}.installToProgress(myIndicator);
      }

      myTask.performInDumbMode(customIndicator);
    }

    void registerStageStarted(@NotNull IdeActivity activity) {
      activity.stageStarted(myTask.getClass());
    }

    String getInfoString() {
      return String.valueOf(myTask);
    }
  }
}
