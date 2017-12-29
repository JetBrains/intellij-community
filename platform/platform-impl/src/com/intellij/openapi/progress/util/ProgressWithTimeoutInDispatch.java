/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProgressWithTimeoutInDispatch extends AbstractProgressIndicatorExBase implements PingProgress {
  private final long myTimeoutMs;
  private final long myStartTimeMs;

  public ProgressWithTimeoutInDispatch(long timeoutMs) {
    super();
    myTimeoutMs = timeoutMs;
    myStartTimeMs = System.currentTimeMillis();
    setIndeterminate(true);
  }

  @Override
  public boolean isCanceled() {
    if (System.currentTimeMillis() - myStartTimeMs >= myTimeoutMs) {
      cancel();
    }
    return super.isCanceled();
  }

  @Override
  public void interact() {}


  /**
   * Cancels the <code>action</code> by the <code>ProcessCanceledException</code> exception
   * after <code>timeoutMs</code> ms on event dispatch thread.
   *
   * The <code>action</code> is not limited by any timeout if it is called on non-dispatch
   * thread, or <code>timeoutMs <= 0</code>, or the thread has progress indicator.
   *
   * @throws ProcessCanceledException
   */
  @ApiStatus.Experimental
  @Nullable
  public static <T> T execInDispatchWithTimeout(@NotNull Computable<T> action, long timeoutMs) throws ProcessCanceledException {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()
        && timeoutMs > 0
        && ProgressManager.getInstance().getProgressIndicator() == null) {
      try {
        return ProgressManager.getInstance().runProcess(action, new ProgressWithTimeoutInDispatch(timeoutMs));
      }
      catch (ProcessCanceledException ex) {
        return null;
      }
    }
    return action.compute();
  }
}
