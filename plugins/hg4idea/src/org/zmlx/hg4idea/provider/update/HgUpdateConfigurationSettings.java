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
    @NotNull public HgUpdateType updateType = HgUpdateType.ONLY_UPDATE;
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

  @Nullable
  @Override
  public HgUpdateConfigurationSettings.State getState() {
    return myState;
  }

  @Override
  public void loadState(HgUpdateConfigurationSettings.State state) {
    myState = state;
  }
}