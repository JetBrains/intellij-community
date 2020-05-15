// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.containers.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
final class DumbServiceTaskQueue {
  private static final Logger LOG = Logger.getInstance(DumbServiceTaskQueue.class);

  private final Set<Object> myQueuedEquivalences = new HashSet<>();
  private final Queue<DumbModeTask> myUpdatesQueue = new Queue<>(5);

  /**
   * Per-task progress indicators. Modified from EDT only.
   * The task is removed from this map after it's finished or when the project is disposed.
   */
  private final Map<DumbModeTask, ProgressIndicatorEx> myProgresses = new ConcurrentHashMap<>();


  void cancelTask(@NotNull DumbModeTask task) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("cancel " + task);
    ProgressIndicatorEx indicator = myProgresses.get(task);
    if (indicator != null) {
      indicator.cancel();
    }
  }

  void disposePendingTasks() {
    myUpdatesQueue.clear();
    myQueuedEquivalences.clear();
    //it was synchronized (myRunWhenSmartQueue) { } here before
    for (DumbModeTask task : new ArrayList<>(myProgresses.keySet())) {
      cancelTask(task);
      Disposer.dispose(task);
    }
  }

  boolean addTaskToQueue(@NotNull DumbModeTask task) {
    if (!myQueuedEquivalences.add(task.getEquivalenceObject())) {
      Disposer.dispose(task);
      return false;
    }

    myProgresses.put(task, new ProgressIndicatorBase());
    Disposer.register(task, () -> {
      ApplicationManager.getApplication().assertIsWriteThread();
      myProgresses.remove(task);
    });
    myUpdatesQueue.addLast(task);
    return true;
  }

  void cancelAllTasks() {
    for (DumbModeTask task : myProgresses.keySet()) {
      cancelTask(task);
    }
  }

  @Nullable
  Pair<DumbModeTask, ProgressIndicatorEx> pollTaskQueue() {
    while (true) {
      if (myUpdatesQueue.isEmpty()) {
        return null;
      }

      DumbModeTask queuedTask = myUpdatesQueue.pullFirst();
      myQueuedEquivalences.remove(queuedTask.getEquivalenceObject());
      ProgressIndicatorEx indicator = myProgresses.get(queuedTask);
      if (indicator.isCanceled()) {
        Disposer.dispose(queuedTask);
        continue;
      }

      return Pair.create(queuedTask, indicator);
    }
  }
}
