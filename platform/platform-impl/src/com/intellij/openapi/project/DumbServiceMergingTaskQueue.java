// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.internal.statistic.IdeActivity;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DumbServiceMergingTaskQueue {
  private final Object myLock = new Object();
  private final Map<Object, DumbModeTask> myTasksQueue = new LinkedHashMap<>();
  private final Map<DumbModeTask, ProgressIndicatorBase> myProgresses = new HashMap<>();

  /**
   * Removes all tasks from Queue without disposing the tasks
   */
  void clearTasksQueue() {
    //we use myProgresses to keep tasks for dispose
    synchronized (myLock) {
      myTasksQueue.clear();
    }
  }

  /**
   * Disposes tasks without removing them from queue,
   * execution of such tasks would skip them later
   */
  void disposePendingTasks() {
    Set<DumbModeTask> disposeQueue = new LinkedHashSet<>(1);
    synchronized (myLock) {
      disposeQueue.addAll(myTasksQueue.values());
      disposeQueue.addAll(myProgresses.keySet());
      myTasksQueue.clear();
      myProgresses.clear();
    }

    for (DumbModeTask task : disposeQueue) {
      Disposer.dispose(task);
    }
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

      myProgresses.put(task, new ProgressIndicatorBase());
      Disposer.register(task, () -> {
        synchronized (myLock) {
          myProgresses.remove(task);
        }
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
          if (indicator == null || indicator.isCanceled()) {
            disposeQueue.add(task);
            continue;
          }

          return new QueuedDumbModeTask(task, indicator);
        }
      }
    } finally {
      for (DumbModeTask task : disposeQueue) {
        Disposer.dispose(task);
      }
    }
  }

  public static class QueuedDumbModeTask {
    private final DumbModeTask myTask;
    private final ProgressIndicatorEx myIndicator;

    public QueuedDumbModeTask(@NotNull DumbModeTask task,
                              @NotNull ProgressIndicatorEx progress) {
      myTask = task;
      myIndicator = progress;
    }

    @NotNull
    public ProgressIndicatorEx getIndicator() {
      return myIndicator;
    }

    public void executeTask() {
      executeTask(null);
    }

    public void executeTask(@Nullable ProgressIndicator customIndicator) {
      try {
        //this is the cancellation check
        myIndicator.checkCanceled();
        myIndicator.setIndeterminate(true);

        if (customIndicator == null) {
          customIndicator = myIndicator;
        } else {
          customIndicator.checkCanceled();
        }

        myTask.performInDumbMode(customIndicator);
      } finally {
        Disposer.dispose(myTask);
      }
    }

    public void registerStageStarted(@NotNull IdeActivity activity) {
      activity.stageStarted(myTask.getClass());
    }
  }
}
