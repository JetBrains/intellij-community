// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.provider.update;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
  name = "HgUpdateConfigurationSettings",
  storages = @Storage(StoragePathMacros.WORKSPACE_FILE)
)
public class HgUpdateConfigurationSettings implements PersistentStateComponent<HgUpdateConfigurationSettings.State> {


  private State myState = new State();

  public static class State {
    public boolean shouldPull = true;
    public @NotNull HgUpdateType updateType = HgUpdateType.ONLY_UPDATE;
    public boolean shouldCommitAfterMerge = false;
  }

  public void setShouldPull(boolean shouldPull) {
    myState.shouldPull = shouldPull;
  }

  public void setUpdateType(@NotNull HgUpdateType updateType) {
    myState.updateType = updateType;
  }

  public void setShouldCommitAfterMerge(boolean shouldCommitAfterMerge) {
    myState.shouldCommitAfterMerge = shouldCommitAfterMerge;
  }

  public boolean shouldPull() {
    return myState.shouldPull;
  }

  public HgUpdateType getUpdateType() {
    return myState.updateType;
  }

  public boolean shouldCommitAfterMerge() {
    return myState.updateType == HgUpdateType.MERGE && myState.shouldCommitAfterMerge;
  }

  @Override
  public @Nullable HgUpdateConfigurationSettings.State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull HgUpdateConfigurationSettings.State state) {
    myState = state;
  }
}