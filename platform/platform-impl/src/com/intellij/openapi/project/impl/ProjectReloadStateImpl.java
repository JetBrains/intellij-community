/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.ProjectReloadState;
import org.jetbrains.annotations.Nullable;

@State(
  name = "ProjectReloadState",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
class ProjectReloadStateImpl extends ProjectReloadState implements PersistentStateComponent<ProjectReloadStateImpl> {
  public static final int UNKNOWN = 0;
  public static final int BEFORE_RELOAD = 1;
  public static final int AFTER_RELOAD = 2;

  public int STATE = UNKNOWN;

  @Override
  public boolean isAfterAutomaticReload() {
    return STATE == AFTER_RELOAD;
  }

  @Override
  public void onBeforeAutomaticProjectReload() {
    STATE = BEFORE_RELOAD;
  }

  @Nullable
  @Override
  public ProjectReloadStateImpl getState() {
    return this;
  }

  @Override
  public void loadState(ProjectReloadStateImpl state) {
    STATE = state.STATE;

    if (STATE == BEFORE_RELOAD) {
      STATE = AFTER_RELOAD;
    }
    else if (STATE == AFTER_RELOAD) {
      STATE = UNKNOWN;
    }
  }
}
