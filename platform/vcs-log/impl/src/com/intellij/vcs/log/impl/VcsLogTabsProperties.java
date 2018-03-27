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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@State(name = "Vcs.Log.Tabs.Properties", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogTabsProperties implements PersistentStateComponent<VcsLogTabsProperties.State> {
  public static final String MAIN_LOG_ID = "MAIN";
  @NotNull private final VcsLogApplicationSettings myAppSettings;
  private State myState = new State();

  public VcsLogTabsProperties(@NotNull VcsLogApplicationSettings appSettings) {
    myAppSettings = appSettings;
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

  public MainVcsLogUiProperties createProperties(@NotNull final String id) {
    myState.TAB_STATES.putIfAbsent(id, new VcsLogUiPropertiesImpl.State());
    return new VcsLogUiPropertiesImpl(myAppSettings) {
      @NotNull
      @Override
      public State getState() {
        State state = myState.TAB_STATES.get(id);
        if (state == null) {
          state = new State();
          myState.TAB_STATES.put(id, state);
        }
        return state;
      }

      @Override
      public void loadState(State state) {
        myState.TAB_STATES.put(id, state);
      }
    };
  }

  public static class State {
    public Map<String, VcsLogUiPropertiesImpl.State> TAB_STATES = ContainerUtil.newTreeMap();
  }
}
