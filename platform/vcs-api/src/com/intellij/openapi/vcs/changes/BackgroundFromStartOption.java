/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.progress.PerformInBackgroundOption;

/**
 * @deprecated use nothing or {@link ALWAYS_BACKGROUND} instead
 */
@Deprecated
public class BackgroundFromStartOption implements PerformInBackgroundOption {
  private final static BackgroundFromStartOption ourInstance = new BackgroundFromStartOption();

  public static PerformInBackgroundOption getInstance() {
    return ourInstance;
  }

  public boolean shouldStartInBackground() {
    return true;
  }

  public void processSentToBackground() {
  }
}
