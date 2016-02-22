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
package com.intellij.vcs.log.data;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.VcsLogSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores UI configuration based on user activity and preferences.
 * Differs from {@link VcsLogSettings} in the fact, that these settings have no representation in the UI settings,
 * and have insignificant effect to the logic of the log, they are just gracefully remember what user prefers to see in the UI.
 */
@State(name = "Vcs.Log.UiProperties", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogUiProperties implements PersistentStateComponent<VcsLogUiProperties.State> {

  private static final int RECENTLY_FILTERED_VALUES_LIMIT = 10;

  private State myState = new State();

  public static class State {
    public boolean SHOW_DETAILS = true;
    public boolean LONG_EDGES_VISIBLE = false;
    public int BEK_SORT_TYPE = 0;
    public boolean SHOW_ROOT_NAMES = false;
    public Deque<UserGroup> RECENTLY_FILTERED_USER_GROUPS = new ArrayDeque<UserGroup>();
    public Deque<UserGroup> RECENTLY_FILTERED_BRANCH_GROUPS = new ArrayDeque<UserGroup>();
    public Map<String, Boolean> HIGHLIGHTERS = ContainerUtil.newTreeMap();
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

  public void addRecentlyFilteredUserGroup(@NotNull List<String> usersInGroup) {
    addRecentGroup(usersInGroup, myState.RECENTLY_FILTERED_USER_GROUPS);
  }

  public void addRecentlyFilteredBranchGroup(@NotNull List<String> valuesInGroup) {
    addRecentGroup(valuesInGroup, myState.RECENTLY_FILTERED_BRANCH_GROUPS);
  }

  private static void addRecentGroup(@NotNull List<String> valuesInGroup, @NotNull Deque<UserGroup> stateField) {
    UserGroup group = new UserGroup();
    group.users = valuesInGroup;
    if (stateField.contains(group)) {
      return;
    }
    stateField.addFirst(group);
    if (stateField.size() > RECENTLY_FILTERED_VALUES_LIMIT) {
      stateField.removeLast();
    }
  }

  @NotNull
  public List<List<String>> getRecentlyFilteredUserGroups() {
    return getRecentGroup(myState.RECENTLY_FILTERED_USER_GROUPS);
  }

  @NotNull
  public List<List<String>> getRecentlyFilteredBranchGroups() {
    return getRecentGroup(myState.RECENTLY_FILTERED_BRANCH_GROUPS);
  }

  @NotNull
  private static List<List<String>> getRecentGroup(Deque<UserGroup> stateField) {
    return ContainerUtil.map2List(stateField, new Function<UserGroup, List<String>>() {
      @Override
      public List<String> fun(UserGroup group) {
        return group.users;
      }
    });
  }

  public boolean areLongEdgesVisible() {
    return myState.LONG_EDGES_VISIBLE;
  }

  public void setLongEdgesVisibility(boolean visible) {
    myState.LONG_EDGES_VISIBLE = visible;
  }

  public int getBekSortType() {
    return myState.BEK_SORT_TYPE;
  }

  public void setBek(int bekSortType) {
    myState.BEK_SORT_TYPE = bekSortType;
  }

  public boolean isShowRootNames() {
    return myState.SHOW_ROOT_NAMES;
  }

  public void setShowRootNames(boolean isShowRootNames) {
    myState.SHOW_ROOT_NAMES = isShowRootNames;
  }

  public boolean isHighlighterEnabled(@NotNull String id) {
    Boolean result = myState.HIGHLIGHTERS.get(id);
    return result != null ? result : true; // new highlighters get enabled by default
  }

  public void enableHighlighter(@NotNull String id, boolean value) {
    myState.HIGHLIGHTERS.put(id, value);
  }

  public static class UserGroup {
    public List<String> users = new ArrayList<String>();

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      UserGroup group = (UserGroup)o;
      if (!users.equals(group.users)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return users.hashCode();
    }
  }

}
