// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
import com.intellij.vcs.log.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

@State(
  name = "Git.Log.External.Tabs.Properties",
  storages = {
    @Storage(value = "git.external.log.tabs.xml", roamingType = RoamingType.DISABLED)
  }
)
public class GitExternalLogTabsProperties implements PersistentStateComponent<GitExternalLogTabsProperties.State>, VcsLogTabsProperties {
  @NotNull private final VcsLogApplicationSettings myAppSettings;
  private State myState = new State();

  public GitExternalLogTabsProperties(@NotNull VcsLogApplicationSettings settings) {
    myAppSettings = settings;
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

  @NotNull
  @Override
  public MainVcsLogUiProperties createProperties(@NotNull String id) {
    myState.TAB_STATES.putIfAbsent(id, new TabState());
    return new MyVcsLogUiProperties(id);
  }

  public static class State {
    @XMap
    public Map<String, TabState> TAB_STATES = ContainerUtil.newTreeMap();
  }

  public static class TabState extends VcsLogUiPropertiesImpl.State {
    @XCollection
    public Deque<VcsLogProjectTabsProperties.RecentGroup> RECENT_USER_FILTERS = new ArrayDeque<>();
    @XCollection
    public Deque<VcsLogProjectTabsProperties.RecentGroup> RECENT_BRANCH_FILTERS = new ArrayDeque<>();
  }

  private class MyVcsLogUiProperties extends VcsLogUiPropertiesImpl<TabState> {
    @NotNull private final String myId;

    public MyVcsLogUiProperties(@NotNull String id) {
      super(myAppSettings);
      myId = id;
    }

    @NotNull
    @Override
    public TabState getState() {
      return myState.TAB_STATES.get(myId);
    }

    @Override
    public void loadState(@NotNull TabState state) {
      myState.TAB_STATES.put(myId, state);
    }

    @Override
    public void addRecentlyFilteredUserGroup(@NotNull List<String> usersInGroup) {
      VcsLogProjectTabsProperties.addRecentGroup(usersInGroup, getState().RECENT_USER_FILTERS);
    }

    @Override
    public void addRecentlyFilteredBranchGroup(@NotNull List<String> valuesInGroup) {
      VcsLogProjectTabsProperties.addRecentGroup(valuesInGroup, getState().RECENT_BRANCH_FILTERS);
    }

    @NotNull
    @Override
    public List<List<String>> getRecentlyFilteredUserGroups() {
      return VcsLogProjectTabsProperties.getRecentGroup(getState().RECENT_USER_FILTERS);
    }

    @NotNull
    @Override
    public List<List<String>> getRecentlyFilteredBranchGroups() {
      return VcsLogProjectTabsProperties.getRecentGroup(getState().RECENT_BRANCH_FILTERS);
    }
  }
}
