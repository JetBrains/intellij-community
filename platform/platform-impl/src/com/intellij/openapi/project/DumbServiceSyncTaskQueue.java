// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public final class DumbServiceSyncTaskQueue {
  private final Object myLock = new Object();
  private boolean myIsRunning = false;
  private final Map<Object, DumbModeTask> myTasksQueue = new LinkedHashMap<>();

  /**
   * It is possible to have yet another synchronous task execution from
   * another synchronous task execution (e.g. when project roots are changes,
   * or in IDEA-240591). Instead of running recursive tasks in-place, we
   * queue these tasks and execute them before we quit from the very first
   * {@link #runTaskSynchronously(DumbModeTask)}. This behaviour is somewhat
   * similar to what we have in the GUI version of {@link DumbServiceImpl}
   */
  public void runTaskSynchronously(@NotNull DumbModeTask task) {
    synchronized (myLock) {
      if (myIsRunning) {
        myTasksQueue.put(task.getEquivalenceObject(), task);
        return;
      }

      myIsRunning = true;
    }

    while (task != null) {
      try {
        doRunTaskSynchronously(task);
      }
      finally {
        synchronized (myLock) {
          if (myTasksQueue.isEmpty()) {
            myIsRunning = false;
            task = null;
          }
          else {
            Object next = myTasksQueue.keySet().iterator().next();
            task = myTasksQueue.remove(next);
          }
        }
      }
    }
  }

  private static void doRunTaskSynchronously(@NotNull DumbModeTask task) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      indicator = new EmptyProgressIndicator();
    }

    indicator.pushState();
    ((CoreProgressManager)ProgressManager.getInstance()).suppressPrioritizing();
    try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing task", HeavyProcessLatch.Type.Indexing)) {
      task.performInDumbMode(indicator);
    }
    finally {
      ((CoreProgressManager)ProgressManager.getInstance()).restorePrioritizing();
      indicator.popState();
      Disposer.dispose(task);
    }
  }
}
