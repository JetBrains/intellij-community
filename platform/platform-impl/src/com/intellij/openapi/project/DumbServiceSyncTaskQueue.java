// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.project.DumbServiceMergingTaskQueue.QueuedDumbModeTask;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public final class DumbServiceSyncTaskQueue {
  private final Project myProject;
  private final AtomicBoolean myIsRunning = new AtomicBoolean(false);
  private final DumbServiceMergingTaskQueue myTaskQueue;

  public DumbServiceSyncTaskQueue(@NotNull Project project, @NotNull DumbServiceMergingTaskQueue queue) {
    myProject = project;
    myTaskQueue = queue;
  }

  /**
   * It is possible to have yet another synchronous task execution from
   * another synchronous task execution (e.g. when project roots are changes,
   * or in IDEA-240591). Instead of running recursive tasks in-place, we
   * queue these tasks and execute them before we quit from the very first
   * {@code runTaskSynchronously()}. This behaviour is somewhat
   * similar to what we have in the GUI version of {@link DumbServiceImpl}
   */
  public void runTaskSynchronously(@NotNull DumbModeTask task) {
    myTaskQueue.addTask(task);

    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      processQueueAfterWriteAction();
    }
    else {
      processQueue();
    }
  }

  private void processQueue() {
    if (!myIsRunning.compareAndSet(false, true)) return;

    Runnable runnable = () -> {
      try {
        while (true) {
          try (QueuedDumbModeTask nextTask = myTaskQueue.extractNextTask()) {
            if (nextTask == null) break;
            doRunTaskSynchronously(nextTask);
          }
          catch (ProcessCanceledException ignored) {
            Logger.getInstance(DumbServiceSyncTaskQueue.class).info("Canceled dumb mode task. Continue to the following task (if any).");
          }
        }
      }
      finally {
        myIsRunning.set(false);
      }
    };

    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      runnable.run();
    }
    else {
      ProgressManager.getInstance().runProcess(runnable, new EmptyProgressIndicator());
    }
  }

  private static void doRunTaskSynchronously(@NotNull QueuedDumbModeTask task) {
    ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    Logger.getInstance(DumbServiceSyncTaskQueue.class).assertTrue(indicator != null, "There should be global progress indicator");

    indicator.pushState();
    ((CoreProgressManager)ProgressManager.getInstance()).suppressPrioritizing();
    try {
      task.executeTask(indicator);
    }
    finally {
      ((CoreProgressManager)ProgressManager.getInstance()).restorePrioritizing();
      indicator.popState();
    }
  }

  private void processQueueAfterWriteAction() {
    Disposable listenerDisposable = Disposer.newDisposable();
    ApplicationManager.getApplication().addApplicationListener(new ApplicationListener() {
      @Override
      public void afterWriteActionFinished(@NotNull Object action) {
        try {
          processQueue();
        }
        finally {
          Disposer.dispose(listenerDisposable);
        }
      }
    }, listenerDisposable);
  }
}
