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
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

class CallbackData {
  private final static Logger LOG = Logger.getInstance(CallbackData.class);

  @NotNull private final Runnable myCallback;
  @NotNull private final Runnable myWrapperStarter;

  CallbackData(@NotNull Runnable callback, @NotNull Runnable wrapperStarter) {
    myCallback = callback;
    myWrapperStarter = wrapperStarter;
  }

  @NotNull
  public Runnable getCallback() {
    return myCallback;
  }

  @NotNull
  public Runnable getWrapperStarter() {
    return myWrapperStarter;
  }

  @NotNull
  public static CallbackData create(@NotNull Project project,
                                    @NotNull InvokeAfterUpdateMode mode,
                                    @NotNull Runnable afterUpdate,
                                    @Nullable String title,
                                    @Nullable ModalityState state) {
    return mode.isSilent() ? createSilent(project, mode, afterUpdate) : createInteractive(project, mode, afterUpdate, title, state);
  }

  @NotNull
  private static CallbackData createSilent(@NotNull Project project, @NotNull InvokeAfterUpdateMode mode, @NotNull Runnable afterUpdate) {
    Consumer<Runnable> callbackCaller = mode.isCallbackOnAwt()
                                        ? ApplicationManager.getApplication()::invokeLater
                                        : ApplicationManager.getApplication()::executeOnPooledThread;
    Runnable callback = () -> {
      logUpdateFinished(project, mode);
      if (!project.isDisposed()) afterUpdate.run();
    };
    return new CallbackData(() -> callbackCaller.accept(callback), EmptyRunnable.INSTANCE);
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
    return new CallbackData(callback, () -> ProgressManager.getInstance().run(task));
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

  private static void logUpdateFinished(@NotNull Project project, @NotNull InvokeAfterUpdateMode mode) {
    LOG.debug(mode + " changes update finished for project " + project.getName());
  }
}
