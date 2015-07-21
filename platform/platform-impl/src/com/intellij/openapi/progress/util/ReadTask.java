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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

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
   */
  public abstract void computeInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException;

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
  public void runBackgroundProcess(@NotNull final ProgressIndicator indicator) throws ProcessCanceledException {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        computeInReadAction(indicator);
      }
    });
  }
}
