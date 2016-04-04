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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A computation that needs to be run in background and inside a read action, and canceled whenever a write action is about to occur. 
 * 
 * @see com.intellij.openapi.progress.util.ProgressIndicatorUtils#scheduleWithWriteActionPriority(ReadTask) 
 * 
 */
public abstract class ReadTask {
  /**
   * Performs the computation.
   * Is invoked inside a read action and under a progress indicator that's canceled when a write action is about to occur.
   * For tasks that have some Swing thread activity afterwards (e.g. applying changes, showing dialogs etc),
   * use {@link #performInReadAction(ProgressIndicator)} instead
   */
  public void computeInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    throw new UnsupportedOperationException();
  }

  /**
   * Performs the computation.
   * Is invoked inside a read action and under a progress indicator that's canceled when a write action is about to occur.
   * @return an action that should be performed later on Swing thread if no write actions have happened before that
   */
  @Nullable
  public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
    computeInReadAction(indicator);
    return null;
  }

  /**
   * Is invoked on Swing thread whenever the computation is canceled by a write action.
   * A likely implementation is to restart the computation, maybe based on the new state of the system.
   */
  public abstract void onCanceled(@NotNull ProgressIndicator indicator);

  /**
   * Is invoked on a background thread. The responsibility of this method is to start a read action and 
   * call {@link #computeInReadAction(ProgressIndicator)}. Overriders might also do something else.
   * For example, use {@link com.intellij.openapi.project.DumbService#runReadActionInSmartMode(Runnable)}.
   * @param indicator the progress indicator of the background thread
   */
  public Continuation runBackgroundProcess(@NotNull final ProgressIndicator indicator) throws ProcessCanceledException {
    return ApplicationManager.getApplication().runReadAction(new Computable<Continuation>() {
      @Override
      public Continuation compute() {
        return performInReadAction(indicator);
      }
    });
  }

  /**
   * An object representing the action that should be done on Swing thread after the background computation is finished.
   * It's invoked only if tasks' progress indicator hasn't been canceled since the task has ended.
   */
  public static final class Continuation {
    private final Runnable myAction;
    private final ModalityState myModalityState;

    /**
     * @param action code to be executed in Swing thread in default modality state
     * @see ModalityState#defaultModalityState()
     */
    public Continuation(@NotNull Runnable action) {
      this(action, ModalityState.defaultModalityState());
    }

    /**
     * @param action code to be executed in Swing thread in default modality state
     * @param modalityState modality state when the action is to be executed
     */
    public Continuation(@NotNull Runnable action, @NotNull ModalityState modalityState) {
      myAction = action;
      myModalityState = modalityState;
    }

    /**
     * @return modality state when {@link #getAction()} is to be executed
     */
    @NotNull
    public ModalityState getModalityState() {
      return myModalityState;
    }

    /**
     * @return runnable to be executed in Swing thread in default modality state
     */
    @NotNull
    public Runnable getAction() {
      return myAction;
    }
  }
}
