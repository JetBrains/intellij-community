// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
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
  public void loadState(@NotNull State state) {
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
      public void loadState(@NotNull State state) {
        myState.TAB_STATES.put(id, state);
      }
    };
  }

  public void addTab(@NotNull String tabId) {
    myState.OPEN_TABS.add(tabId);
  }

  public void removeTab(@NotNull String tabId) {
    myState.OPEN_TABS.remove(tabId);
  }

  @NotNull
  public List<String> getTabs() {
    return ContainerUtil.newArrayList(myState.OPEN_TABS);
  }

  public static class State {
    public Map<String, VcsLogUiPropertiesImpl.State> TAB_STATES = ContainerUtil.newTreeMap();
    public LinkedHashSet<String> OPEN_TABS = ContainerUtil.newLinkedHashSet();
  }
}
