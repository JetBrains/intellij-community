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

import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

/**
 * A computation that needs to be run in background and inside a read action, and canceled whenever a write action is about to occur. 
 * 
 * @see com.intellij.openapi.progress.util.ProgressIndicatorUtils#scheduleWithWriteActionPriority(ReadTask) 
 * 
 */
public interface ReadTask {
  /**
   * Performs the computation.
   * Is invoked inside a read action and under a progress indicator that's canceled when a write action is about to occur.
   */
  void computeInReadAction(@NotNull ProgressIndicator indicator);

  /**
   * Is invoked on the background computation thread whenever the computation is canceled by a write action.
   * A likely implementation is to restart the computation, maybe based on the new state of the system.
   */
  void onCanceled(@NotNull ProgressIndicator indicator);

}
