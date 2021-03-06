// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.progress;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.QueueProcessor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.util.concurrency.QueueProcessor.ThreadToUse;

/**
 * Runs backgroundable tasks one by one.
 * To add a task to the queue use {@link #run(Task.Backgroundable)}
 * BackgroundTaskQueue may have a title - this title will be used if the task which is currently running doesn't have a title.
 */
@SomeQueue
public class BackgroundTaskQueue {
  @Nls(capitalization = Nls.Capitalization.Title) @NotNull protected final String myTitle;
  @NotNull protected final QueueProcessor<TaskData> myProcessor;

  @NotNull private final Object TEST_TASK_LOCK = new Object();
  private volatile boolean myForceAsyncInTests;

  public BackgroundTaskQueue(@Nullable Project project, @NlsContexts.ProgressTitle @NotNull String title) {
    myTitle = title;

    Condition<?> disposeCondition = project != null ? project.getDisposed() : ApplicationManager.getApplication().getDisposed();
    myProcessor = new QueueProcessor<>(TaskData::consume, true, ThreadToUse.AWT, disposeCondition);
  }

  public void clear() {
    myProcessor.clear();
  }

  public boolean isEmpty() {
    return myProcessor.isEmpty();
  }

  public void waitForTasksToFinish() {
    myProcessor.waitFor();
  }

  public void run(@NotNull Task.Backgroundable task) {
    run(task, null, null);
  }

  public void run(@NotNull Task.Backgroundable task, @Nullable ModalityState modalityState, @Nullable ProgressIndicator indicator) {
    BackgroundableTaskData taskData = new BackgroundableTaskData(task, modalityState, indicator);
    if (!myForceAsyncInTests && ApplicationManager.getApplication().isUnitTestMode()) {
      runTaskInCurrentThread(taskData);
    }
    else {
      myProcessor.add(taskData, modalityState);
    }
  }


  @TestOnly
  public void setForceAsyncInTests(boolean value, @NotNull Disposable disposable) {
    myForceAsyncInTests = value;
    Disposer.register(disposable, () -> myForceAsyncInTests = false);
  }

  private void runTaskInCurrentThread(@NotNull BackgroundableTaskData data) {
    Task.Backgroundable task = data.myTask;

    ProgressIndicator indicator = data.myIndicator;
    if (indicator == null) indicator = new EmptyProgressIndicator();

    ModalityState modalityState = data.myModalityState;
    if (modalityState == null) modalityState = ModalityState.NON_MODAL;

    ProgressManagerImpl pm = (ProgressManagerImpl)ProgressManager.getInstance();

    // prohibit simultaneous execution from different threads
    synchronized (TEST_TASK_LOCK) {
      pm.runProcessWithProgressInCurrentThread(task, indicator, modalityState);
    }
  }

  @FunctionalInterface
  protected interface TaskData extends Consumer<Runnable> {
  }

  protected class BackgroundableTaskData implements TaskData {
    @NotNull private final Task.Backgroundable myTask;
    @Nullable private final ModalityState myModalityState;
    @Nullable private final ProgressIndicator myIndicator;

    BackgroundableTaskData(@NotNull Task.Backgroundable task,
                           @Nullable ModalityState modalityState,
                           @Nullable ProgressIndicator indicator) {
      myTask = task;
      myModalityState = modalityState;
      myIndicator = indicator;
    }

    @Override
    public void consume(@NotNull Runnable continuation) {
      Task.Backgroundable task = myTask;
      Project taskProject = task.getProject();
      if (taskProject != null && taskProject.isDisposed()) {
        continuation.run();
        return;
      }
      ProgressIndicator indicator = myIndicator;
      if (indicator == null) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
          indicator = new EmptyProgressIndicator();
        }
        else {
          // BackgroundableProcessIndicator should be created from EDT
          indicator = new BackgroundableProcessIndicator(task);
        }
      }

      ModalityState modalityState = myModalityState;
      if (modalityState == null) modalityState = ModalityState.NON_MODAL;

      if (StringUtil.isEmptyOrSpaces(task.getTitle())) {
        task.setTitle(myTitle);
      }

      boolean synchronous = task.isHeadless() && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode() && !myForceAsyncInTests ||
                            task.isConditionalModal() && !task.shouldStartInBackground();

      ProgressManagerImpl pm = (ProgressManagerImpl)ProgressManager.getInstance();
      if (synchronous) {
        try {
          pm.runProcessWithProgressSynchronously(task, null);
        }
        finally {
          continuation.run();
        }
      }
      else {
        pm.runProcessWithProgressAsynchronously(task, indicator, continuation, modalityState);
      }
    }
  }
}
