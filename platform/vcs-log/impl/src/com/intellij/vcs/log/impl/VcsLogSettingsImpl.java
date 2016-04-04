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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.vcs.log.VcsLogSettings;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
@State(name = "Vcs.Log.Settings", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogSettingsImpl implements VcsLogSettings, PersistentStateComponent<VcsLogSettingsImpl.State> {

  private State myState = new State();

  public static class State {
    public int RECENT_COMMITS_COUNT = 1000;
    public boolean SHOW_BRANCHES_PANEL = false;
  }

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  @Override
  public int getRecentCommitsCount() {
    return myState.RECENT_COMMITS_COUNT;
  }

  @Override
  public boolean isShowBranchesPanel() {
    return myState.SHOW_BRANCHES_PANEL;
  }

  @Override
  public void setShowBranchesPanel(boolean show) {
    myState.SHOW_BRANCHES_PANEL = show;
  }
}
