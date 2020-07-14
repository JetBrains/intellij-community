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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.intellij.util.ObjectUtils.notNull;

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
    return mode.isSilent() ? createSilent(project, mode, afterUpdate, state) : createInteractive(project, mode, afterUpdate, title, state);
  }

  @NotNull
  private static CallbackData createSilent(@NotNull Project project,
                                           @NotNull InvokeAfterUpdateMode mode,
                                           @NotNull Runnable afterUpdate,
                                           @Nullable ModalityState state) {
    Consumer<Runnable> callbackCaller = mode.isCallbackOnAwt()
                                        ? ApplicationManager.getApplication()::invokeLater
                                        : ApplicationManager.getApplication()::executeOnPooledThread;
    Runnable callback = () -> {
      logUpdateFinished(project, mode);
      if (!project.isDisposed()) afterUpdate.run();
    };
    return new CallbackDataBase(project, afterUpdate, state) {
      @Override
      public void startProgress() {
      }

      @Override
      public void endProgress() {
        callbackCaller.accept(callback);
      }
    };
  }

  @NotNull
  private static CallbackData createInteractive(@NotNull Project project,
                                                @NotNull InvokeAfterUpdateMode mode,
                                                @NotNull Runnable afterUpdate,
                                                String title,
                                                @Nullable ModalityState state) {
    Task task = mode.isSynchronous()
                ? new Waiter(project, afterUpdate, title, mode.isCancellable())
                : new FictiveBackgroundable(project, afterUpdate, title, mode.isCancellable(), state);
    Runnable callback = () -> {
      logUpdateFinished(project, mode);
      setDone(task);
    };
    return new CallbackDataBase(project, afterUpdate, state) {
      @Override
      public void startProgress() {
        ProgressManager.getInstance().run(task);
      }

      @Override
      public void endProgress() {
        callback.run();
      }
    };
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
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          myAfterUpdate.run();
        }
      }, notNull(myModalityState, ModalityState.defaultModalityState()));
    }
  }

  private static void logUpdateFinished(@NotNull Project project, @NotNull InvokeAfterUpdateMode mode) {
    LOG.debug(mode + " changes update finished for project " + project.getName());
  }
}
