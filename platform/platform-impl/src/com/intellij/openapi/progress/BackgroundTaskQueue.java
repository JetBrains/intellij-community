/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.progress;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.QueueProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Runs backgroundable tasks one by one.
 * To add a task to the queue use {@link #run(com.intellij.openapi.progress.Task.Backgroundable)}
 * BackgroundTaskQueue may have a title - this title will be used if the task which is currently running doesn't have a title.
 */
@SomeQueue
public class BackgroundTaskQueue {
  @NotNull private final String myTitle;
  @NotNull private final QueueProcessor<TaskData> myProcessor;

  @NotNull private final Object TEST_TASK_LOCK = new Object();
  private volatile boolean myForceAsyncInTests = false;

  public BackgroundTaskQueue(@Nullable Project project, @NotNull String title) {
    myTitle = title;

    Condition disposeCondition = project != null ? project.getDisposed() : ApplicationManager.getApplication().getDisposed();

    myProcessor = new QueueProcessor<>((data, continuation) -> {
      Task.Backgroundable task = data.task;

      ProgressIndicator indicator = data.indicator;
      if (indicator == null) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
          indicator = new EmptyProgressIndicator();
        }
        else {
          // BackgroundableProcessIndicator should be created from EDT
          indicator = new BackgroundableProcessIndicator(task);
        }
      }

      ModalityState modalityState = data.modalityState;
      if (modalityState == null) modalityState = ModalityState.NON_MODAL;

      if (StringUtil.isEmptyOrSpaces(task.getTitle())) {
        task.setTitle(myTitle);
      }

      boolean synchronous = (task.isHeadless() && !myForceAsyncInTests) ||
                            (task.isConditionalModal() && !task.shouldStartInBackground());

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
    }, true, QueueProcessor.ThreadToUse.AWT, disposeCondition);
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
    TaskData taskData = new TaskData(task, modalityState, indicator);
    if (!myForceAsyncInTests && ApplicationManager.getApplication().isUnitTestMode()) {
      runTaskInCurrentThread(taskData);
    }
    else {
      myProcessor.add(taskData, modalityState);
    }
  }

  private static class TaskData {
    @NotNull public final Task.Backgroundable task;
    @Nullable public final ModalityState modalityState;
    @Nullable public final ProgressIndicator indicator;

    public TaskData(@NotNull Task.Backgroundable task, @Nullable ModalityState modalityState, @Nullable ProgressIndicator indicator) {
      this.task = task;
      this.modalityState = modalityState;
      this.indicator = indicator;
    }
  }

  @TestOnly
  public void setForceAsyncInTests(boolean value, @Nullable Disposable disposable) {
    myForceAsyncInTests = value;
    if (disposable != null) {
      Disposer.register(disposable, new Disposable() {
        @Override
        public void dispose() {
          myForceAsyncInTests = false;
        }
      });
    }
  }

  private void runTaskInCurrentThread(@NotNull TaskData data) {
    Task.Backgroundable task = data.task;

    ProgressIndicator indicator = data.indicator;
    if (indicator == null) indicator = new EmptyProgressIndicator();

    ModalityState modalityState = data.modalityState;
    if (modalityState == null) modalityState = ModalityState.NON_MODAL;

    ProgressManagerImpl pm = (ProgressManagerImpl)ProgressManager.getInstance();

    // prohibit simultaneous execution from different threads
    synchronized (TEST_TASK_LOCK) {
      pm.runProcessWithProgressInCurrentThread(task, indicator, modalityState);
    }
  }
}
