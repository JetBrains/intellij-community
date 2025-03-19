// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.progress;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.QueueProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.function.Consumer;

import static com.intellij.util.concurrency.QueueProcessor.ThreadToUse;

/**
 * Runs backgroundable tasks one by one.
 * To add a task to the queue use {@link #run(Task.Backgroundable)}
 * BackgroundTaskQueue may have a title - this title will be used if the task which is currently running doesn't have a title.
 *
 * @deprecated use Kotlin coroutines with appropriate concurrency limiting (Mutex/Semaphore/Channel)
 */
@Deprecated
public class BackgroundTaskQueue {

  protected final @NlsContexts.ProgressTitle @NotNull String myTitle;
  protected final @NotNull QueueProcessor<TaskData> myProcessor;

  private volatile boolean myForceAsyncInTests;

  public BackgroundTaskQueue(@Nullable Project project,
                             @NlsContexts.ProgressTitle @NotNull String title) {
    myTitle = title;
    myProcessor = new QueueProcessor<>((taskData, continuation) -> taskData.accept(continuation),
                                       true,
                                       ThreadToUse.AWT,
                                       project != null ? project.getDisposed() : ApplicationManager.getApplication().getDisposed());
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
    run(task, ModalityState.nonModal(), null);
  }

  public void run(@NotNull Task.Backgroundable task,
                  @NotNull ModalityState modalityState,
                  @Nullable ProgressIndicator indicator) {
    BackgroundableTaskData taskData = new BackgroundableTaskData(task, modalityState, indicator);
    myProcessor.add(taskData, modalityState);
  }

  @TestOnly
  public void setForceAsyncInTests(boolean value, @NotNull Disposable disposable) {
    myForceAsyncInTests = value;
    Disposer.register(disposable, () -> myForceAsyncInTests = false);
  }

  @FunctionalInterface
  protected interface TaskData extends Consumer<Runnable> {
  }

  private final class BackgroundableTaskData implements TaskData {

    private final @NotNull Task.Backgroundable myTask;
    private final @NotNull ModalityState myModalityState;
    private final @Nullable ProgressIndicator myIndicator;

    BackgroundableTaskData(@NotNull Task.Backgroundable task,
                           @NotNull ModalityState modalityState,
                           @Nullable ProgressIndicator indicator) {
      myTask = task;
      myModalityState = modalityState;
      myIndicator = indicator;
    }

    @Override
    public void accept(@NotNull Runnable continuation) {
      Project taskProject = myTask.getProject();
      if (taskProject != null && taskProject.isDisposed()) {
        continuation.run();
        return;
      }

      if (StringUtil.isEmptyOrSpaces(myTask.getTitle())) {
        myTask.setTitle(myTitle);
      }

      boolean synchronous =
        myTask.isHeadless() && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode() && !myForceAsyncInTests ||
        myTask.isConditionalModal() && !myTask.shouldStartInBackground();

      if (synchronous) {
        try {
          getProgressManager().runProcessWithProgressSynchronously(myTask);
        }
        finally {
          continuation.run();
        }
      }
      else {
        getProgressManager().runProcessWithProgressAsynchronously(myTask,
                                                                  getIndicator(),
                                                                  continuation,
                                                                  myModalityState);
      }
    }

    private @NotNull ProgressIndicator getIndicator() {
      return myIndicator != null ?
             myIndicator :
             ApplicationManager.getApplication().isHeadlessEnvironment() ?
             new EmptyProgressIndicator() :
             new BackgroundableProcessIndicator(myTask); // BackgroundableProcessIndicator should be created from EDT
    }
  }

  private static @NotNull ProgressManagerImpl getProgressManager() {
    return (ProgressManagerImpl)ProgressManager.getInstance();
  }
}
