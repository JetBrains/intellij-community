// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.internal.statistic.IdeActivity;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbServiceMergingTaskQueue.QueuedDumbModeTask;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

final class DumbServiceGuiTaskQueue {
  private static final Logger LOG = Logger.getInstance(DumbServiceGuiTaskQueue.class);

  private final Project myProject;
  private final DumbServiceMergingTaskQueue myTaskQueue;

  /**
   * Per-task progress indicators. Modified from EDT only.
   * The task is removed from this map after it's finished or when the project is disposed.
   */

  DumbServiceGuiTaskQueue(@NotNull Project project,
                          @NotNull DumbServiceMergingTaskQueue queue) {
    myProject = project;
    myTaskQueue = queue;
  }

  void processTasksWithProgress(@NotNull Consumer<ProgressIndicatorEx> bindProgress,
                                @NotNull IdeActivity activity) {
    while (true) {
      //we do jump in EDT to
      if (myProject.isDisposed()) break;

      try (QueuedDumbModeTask pair = myTaskQueue.extractNextTask()) {
        if (pair == null) break;

        bindProgress.accept(pair.getIndicator());
        pair.registerStageStarted(activity);

        try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Performing indexing tasks", HeavyProcessLatch.Type.Indexing)) {
          runSingleTask(pair);
        }
      }
    }
  }

  private static void runSingleTask(@NotNull final QueuedDumbModeTask task) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("Running dumb mode task: " + task.getInfoString());

    // nested runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks
    ProgressManager.getInstance().runProcess(() -> {
      try {
        task.executeTask();
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Throwable unexpected) {
        LOG.error("Failed to execute task " + task + ". " + unexpected.getMessage(), unexpected);
      }
    }, task.getIndicator());
  }
}
