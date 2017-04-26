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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.progress.PerformInBackgroundOption;

/**
 * Created by kirylch on 4/12/2017.
 */
public interface ActionInfoEx extends ActionInfo {
  static boolean activeToolWindowOnUpdate(ActionInfo action) {
    if(action instanceof ActionInfoEx) {
      return ((ActionInfoEx)action).activeToolWindowOnUpdate();
    }
    return true;
  }

  static PerformInBackgroundOption maybeOverrideBackGroundOption(ActionInfo action, PerformInBackgroundOption option) {
    if(action instanceof ActionInfoEx) {
      return ((ActionInfoEx)action).maybeOverrideBackGroundOption(option);
    }
    return option;
  }

  boolean activeToolWindowOnUpdate();
  PerformInBackgroundOption maybeOverrideBackGroundOption(PerformInBackgroundOption option);
}
