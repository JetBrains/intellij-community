// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.DumbServiceMergingTaskQueue.QueuedDumbModeTask;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DumbServiceSyncTaskQueue {
  private final AtomicBoolean myIsRunning = new AtomicBoolean(false);
  private final DumbServiceMergingTaskQueue myTaskQueue;

  public DumbServiceSyncTaskQueue(@NotNull DumbServiceMergingTaskQueue queue) {
    myTaskQueue = queue;
  }

  /**
   * It is possible to have yet another synchronous task execution from
   * another synchronous task execution (e.g. when project roots are changes,
   * or in IDEA-240591). Instead of running recursive tasks in-place, we
   * queue these tasks and execute them before we quit from the very first
   * {@link #runTaskSynchronously(DumbModeTask)}. This behaviour is somewhat
   * similar to what we have in the GUI version of {@link DumbServiceImpl}
   */
  public void runTaskSynchronously(@NotNull DumbModeTask task) {
    myTaskQueue.addTask(task);

    if (!myIsRunning.compareAndSet(false, true)) return;

    try {
      processQueue();
    } finally {
      myIsRunning.set(false);
    }
  }

  private void processQueue() {
    while (true) {
      try (QueuedDumbModeTask nextTask = myTaskQueue.extractNextTask()) {
        if (nextTask == null) break;
        doRunTaskSynchronously(nextTask);
      }
    }
  }

  private static void doRunTaskSynchronously(@NotNull QueuedDumbModeTask task) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      indicator = new EmptyProgressIndicator();
    }

    indicator.pushState();
    ((CoreProgressManager)ProgressManager.getInstance()).suppressPrioritizing();
    try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing task", HeavyProcessLatch.Type.Indexing)) {
      task.executeTask(indicator);
    }
    finally {
      ((CoreProgressManager)ProgressManager.getInstance()).restorePrioritizing();
      indicator.popState();
    }
  }
}
