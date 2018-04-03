// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.ProjectReloadState;
import org.jetbrains.annotations.NotNull;
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
  public void loadState(@NotNull ProjectReloadStateImpl state) {
    STATE = state.STATE;

    if (STATE == BEFORE_RELOAD) {
      STATE = AFTER_RELOAD;
    }
    else if (STATE == AFTER_RELOAD) {
      STATE = UNKNOWN;
    }
  }
}
