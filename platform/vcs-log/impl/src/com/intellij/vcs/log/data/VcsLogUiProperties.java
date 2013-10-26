/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.vcs.log.VcsLogSettings;
import org.jetbrains.annotations.Nullable;

/**
 * Stores UI configuration based on user activity and preferences.
 * Differs from {@link VcsLogSettings} in the fact, that these settings have no representation in the UI settings,
 * and have insignificant effect to the logic of the log, they are just gracefully remember what user prefers to see in the UI.
 */
@State(name = "Vcs.Log.UiProperties", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogUiProperties implements PersistentStateComponent<VcsLogUiProperties.State> {

  private State myState = new State();

  public static class State {
    public boolean SHOW_DETAILS = false;
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

  /**
   * Returns true if the details pane (which shows commit meta-data, such as the full commit message, commit date, all references, etc.)
   * should be visible when the log is loaded; returns false if it should be hidden by default.
   */
  public boolean isShowDetails() {
    return myState.SHOW_DETAILS;
  }

  public void setShowDetails(boolean showDetails) {
    myState.SHOW_DETAILS = showDetails;
  }

}
