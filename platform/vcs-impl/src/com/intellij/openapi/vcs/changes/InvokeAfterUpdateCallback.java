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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class InvokeAfterUpdateCallback {
  private final static Logger LOG = Logger.getInstance(InvokeAfterUpdateCallback.class);

  interface Callback {
    void startProgress();

    void endProgress();

    void handleStoppedQueue();
  }

  @NotNull
  public static Callback create(@NotNull Project project,
                                @NotNull InvokeAfterUpdateMode mode,
                                @NotNull Runnable afterUpdate,
                                @Nullable String title) {
    if (mode.isSilent()) {
      return new SilentCallback(project, afterUpdate, mode.isCallbackOnAwt());
    }
    else {
      return new ProgressCallback(project, afterUpdate, mode.isSynchronous(), mode.isCancellable(), title);
    }
  }

  private abstract static class CallbackBase implements Callback {
    protected final Project myProject;
    private final Runnable myAfterUpdate;

    CallbackBase(@NotNull Project project, @NotNull Runnable afterUpdate) {
      myProject = project;
      myAfterUpdate = afterUpdate;
    }

    protected final void invokeCallback() {
      LOG.debug("changes update finished for project " + myProject.getName());
      if (!myProject.isDisposed()) myAfterUpdate.run();
    }
  }

  private static class SilentCallback extends CallbackBase {
    private final boolean myCallbackOnAwt;

    SilentCallback(@NotNull Project project,
                   @NotNull Runnable afterUpdate,
                   boolean callbackOnAwt) {
      super(project, afterUpdate);
      myCallbackOnAwt = callbackOnAwt;
    }

    @Override
    public void startProgress() {
    }

    @Override
    public void endProgress() {
      scheduleCallback();
    }

    @Override
    public void handleStoppedQueue() {
      scheduleCallback();
    }

    private void scheduleCallback() {
      if (myCallbackOnAwt) {
        ApplicationManager.getApplication().invokeLater(this::invokeCallback);
      }
      else {
        ApplicationManager.getApplication().executeOnPooledThread(this::invokeCallback);
      }
    }
  }

  private static class ProgressCallback extends CallbackBase {
    private final boolean mySynchronous;
    private final boolean myCanBeCancelled;
    private final @NlsContexts.ProgressTitle String myTaskTitle;

    @NotNull private final Semaphore mySemaphore = new Semaphore(1);

    ProgressCallback(@NotNull Project project,
                     @NotNull Runnable afterUpdate,
                     boolean synchronous,
                     boolean canBeCancelled,
                     String title) {
      super(project, afterUpdate);
      mySynchronous = synchronous;
      myCanBeCancelled = canBeCancelled;
      myTaskTitle = VcsBundle.message("change.list.manager.wait.lists.synchronization", title);
    }

    @Override
    public void startProgress() {
      if (mySynchronous) {
        new ModalWaiter().queue();
      }
      else {
        new BackgroundableWaiter().queue();
      }
    }

    @Override
    public void endProgress() {
      mySemaphore.up();
    }

    @Override
    public void handleStoppedQueue() {
      ApplicationManager.getApplication().invokeLater(this::invokeCallback);
    }

    private void awaitSemaphore(@NotNull ProgressIndicator indicator) {
      indicator.setIndeterminate(true);
      indicator.setText2(VcsBundle.message("commit.wait.util.synched.text"));
      ProgressIndicatorUtils.awaitWithCheckCanceled(mySemaphore, indicator);
    }

    private class ModalWaiter extends Task.Modal {
      ModalWaiter() {
        super(ProgressCallback.this.myProject, myTaskTitle, myCanBeCancelled);
        setCancelText(VcsBundle.message("button.skip"));
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        awaitSemaphore(indicator);
      }

      @Override
      public void onFinished() {
        invokeCallback();
      }
    }

    private class BackgroundableWaiter extends Task.Backgroundable {
      BackgroundableWaiter() {
        super(ProgressCallback.this.myProject, myTaskTitle, myCanBeCancelled);
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        awaitSemaphore(indicator);
      }

      @Override
      public void onSuccess() {
        invokeCallback();
      }

      @Override
      public boolean isHeadless() {
        return false;
      }
    }
  }
}
