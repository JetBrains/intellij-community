/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;

class InvokeAfterUpdateCallback {
  private final static Logger LOG = Logger.getInstance(InvokeAfterUpdateCallback.class);

  interface CallbackData {
    void startProgress();

    void endProgress();

    void handleStoppedQueue();
  }

  @NotNull
  public static CallbackData create(@NotNull Project project,
                                    @NotNull InvokeAfterUpdateMode mode,
                                    @NotNull Runnable afterUpdate,
                                    @Nullable String title,
                                    @Nullable ModalityState state) {
    if (mode.isSilent()) {
      return new SilentCallbackData(project, afterUpdate, mode.isCallbackOnAwt(), state);
    }
    else {
      return new TaskCallbackData(project, afterUpdate, mode.isSynchronous(), mode.isCancellable(), title, state);
    }
  }

  private static void setDone(@NotNull Task task) {
    if (task instanceof Waiter) {
      ((Waiter)task).done();
    }
    else if (task instanceof FictiveBackgroundable) {
      ((FictiveBackgroundable)task).done();
    }
    else {
      throw new IllegalArgumentException("Unknown task type " + task.getClass());
    }
  }

  private abstract static class CallbackDataBase implements CallbackData {
    private final Project myProject;
    private final Runnable myAfterUpdate;
    private final ModalityState myModalityState;

    CallbackDataBase(@NotNull Project project, @NotNull Runnable afterUpdate, @Nullable ModalityState modalityState) {
      myProject = project;
      myAfterUpdate = afterUpdate;
      myModalityState = modalityState;
    }

    @Override
    public void handleStoppedQueue() {
      ModalityState modalityState = notNull(myModalityState, ModalityState.defaultModalityState());
      ApplicationManager.getApplication().invokeLater(this::invokeCallback, modalityState);
    }

    protected final void invokeCallback() {
      LOG.debug("changes update finished for project " + myProject.getName());
      if (!myProject.isDisposed()) myAfterUpdate.run();
    }
  }

  private static class SilentCallbackData extends CallbackDataBase {
    private final boolean myCallbackOnAwt;

    SilentCallbackData(@NotNull Project project,
                       @NotNull Runnable afterUpdate,
                       boolean callbackOnAwt,
                       @Nullable ModalityState state) {
      super(project, afterUpdate, state);
      myCallbackOnAwt = callbackOnAwt;
    }

    @Override
    public void startProgress() {
    }

    @Override
    public void endProgress() {
      if (myCallbackOnAwt) {
        ApplicationManager.getApplication().invokeLater(this::invokeCallback);
      }
      else {
        ApplicationManager.getApplication().executeOnPooledThread(this::invokeCallback);
      }
    }
  }

  private static class TaskCallbackData extends CallbackDataBase {
    private final Task myTask;

    TaskCallbackData(@NotNull Project project,
                     @NotNull Runnable afterUpdate,
                     boolean synchronous,
                     boolean canBeCancelled,
                     String title,
                     @Nullable ModalityState state) {
      super(project, afterUpdate, state);
      myTask = synchronous
               ? new Waiter(project, afterUpdate, title, canBeCancelled)
               : new FictiveBackgroundable(project, afterUpdate, title, canBeCancelled, state);
    }

    @Override
    public void startProgress() {
      ProgressManager.getInstance().run(myTask);
    }

    @Override
    public void endProgress() {
      setDone(myTask);
    }
  }

  private static class Waiter extends Task.Modal {
    @NotNull private final Runnable myRunnable;
    @NotNull private final AtomicBoolean myStarted = new AtomicBoolean();
    @NotNull private final Semaphore mySemaphore = new Semaphore();

    Waiter(@NotNull Project project, @NotNull Runnable runnable, String title, boolean cancellable) {
      super(project, VcsBundle.message("change.list.manager.wait.lists.synchronization", title), cancellable);
      myRunnable = runnable;
      mySemaphore.down();
      setCancelText(VcsBundle.message("button.skip"));
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      indicator.setText2(VcsBundle.message("commit.wait.util.synched.text"));

      if (!myStarted.compareAndSet(false, true)) {
        LOG.error("Waiter running under progress being started again.");
      }
      else {
        ProgressIndicatorUtils.awaitWithCheckCanceled(mySemaphore, indicator);
      }
    }

    @Override
    public void onCancel() {
      onSuccess();
    }

    @Override
    public void onSuccess() {
      // Be careful with changes here as "Waiter.onSuccess()" is explicitly invoked from "FictiveBackgroundable"
      if (!myProject.isDisposed()) {
        myRunnable.run();
      }
    }

    public void done() {
      mySemaphore.up();
    }
  }

  private static class FictiveBackgroundable extends Task.Backgroundable {
    @NotNull private final Waiter myWaiter;
    @Nullable private final ModalityState myState;

    FictiveBackgroundable(@NotNull Project project,
                          @NotNull Runnable runnable,
                          String title,
                          boolean cancellable,
                          @Nullable ModalityState state) {
      super(project, VcsBundle.message("change.list.manager.wait.lists.synchronization", title), cancellable);
      myState = state;
      myWaiter = new Waiter(project, runnable, title, cancellable);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myWaiter.run(indicator);
      runOrInvokeLaterAboveProgress(() -> myWaiter.onSuccess(), notNull(myState, ModalityState.NON_MODAL), myProject);
    }

    @Override
    public boolean isHeadless() {
      return false;
    }

    public void done() {
      myWaiter.done();
    }
  }
}
