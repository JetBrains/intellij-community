// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.*;
import static java.util.Comparator.comparingInt;

@State(name = "Vcs.Log.Tabs.Properties", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogTabsProperties implements PersistentStateComponent<VcsLogTabsProperties.State> {
  public static final String MAIN_LOG_ID = "MAIN";
  private static final int RECENTLY_FILTERED_VALUES_LIMIT = 10;
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

    // to remove after 2018.3 release
    migrateRecentItems();
  }

  private void migrateRecentItems() {
    if (isEmpty(myState.RECENTLY_FILTERED_BRANCH_GROUPS) && isEmpty(myState.RECENTLY_FILTERED_USER_GROUPS)) {

      myState.RECENTLY_FILTERED_BRANCH_GROUPS = new ArrayDeque<>();
      myState.RECENTLY_FILTERED_USER_GROUPS = new ArrayDeque<>();

      Multiset<RecentGroup> branchFrequencies = HashMultiset.create();
      Multiset<RecentGroup> userFrequencies = HashMultiset.create();
      for (Map.Entry<String, VcsLogUiPropertiesImpl.State> entry : myState.TAB_STATES.entrySet()) {
        VcsLogUiPropertiesImpl.State s = entry.getValue();
        branchFrequencies.addAll(map(s.RECENTLY_FILTERED_BRANCH_GROUPS, RecentGroup::new));
        userFrequencies.addAll(map(s.RECENTLY_FILTERED_USER_GROUPS, RecentGroup::new));
        s.RECENTLY_FILTERED_BRANCH_GROUPS.clear();
        s.RECENTLY_FILTERED_USER_GROUPS.clear();
      }

      List<RecentGroup> sortedBranches = sorted(branchFrequencies.elementSet(), comparingInt(value -> -branchFrequencies.count(value)));
      List<RecentGroup> sortedUsers = sorted(userFrequencies.elementSet(), comparingInt(value -> -userFrequencies.count(value)));

      myState.RECENTLY_FILTERED_BRANCH_GROUPS.addAll(getFirstItems(sortedBranches, RECENTLY_FILTERED_VALUES_LIMIT));
      myState.RECENTLY_FILTERED_USER_GROUPS.addAll(getFirstItems(sortedUsers, RECENTLY_FILTERED_VALUES_LIMIT));
    }
  }

  @NotNull
  public MainVcsLogUiProperties createProperties(@NotNull final String id) {
    myState.TAB_STATES.putIfAbsent(id, new VcsLogUiPropertiesImpl.State());
    return new MyVcsLogUiPropertiesImpl(id);
  }

  public void addTab(@NotNull String tabId) {
    myState.OPEN_TABS.add(tabId);
  }

  public void removeTab(@NotNull String tabId) {
    myState.OPEN_TABS.remove(tabId);
  }

  @NotNull
  public List<String> getTabs() {
    return newArrayList(myState.OPEN_TABS);
  }

  private static void addRecentGroup(@NotNull List<String> valuesInGroup, @NotNull Deque<RecentGroup> stateField) {
    RecentGroup group = new RecentGroup();
    group.FILTER_VALUES = valuesInGroup;
    if (stateField.contains(group)) {
      return;
    }
    stateField.addFirst(group);
    while (stateField.size() > RECENTLY_FILTERED_VALUES_LIMIT) {
      stateField.removeLast();
    }
  }

  @NotNull
  private static List<List<String>> getRecentGroup(@NotNull Deque<RecentGroup> stateField) {
    return map2List(stateField, group -> group.FILTER_VALUES);
  }

  public static class State {
    public Map<String, VcsLogUiPropertiesImpl.State> TAB_STATES = newTreeMap();
    public LinkedHashSet<String> OPEN_TABS = newLinkedHashSet();
    public Deque<RecentGroup> RECENTLY_FILTERED_USER_GROUPS = new ArrayDeque<>();
    public Deque<RecentGroup> RECENTLY_FILTERED_BRANCH_GROUPS = new ArrayDeque<>();
  }

  public static class RecentGroup {
    @XCollection
    public List<String> FILTER_VALUES = newArrayList();

    public RecentGroup() {
    }

    public RecentGroup(@NotNull VcsLogUiPropertiesImpl.UserGroup oldGroup) {
      FILTER_VALUES.addAll(oldGroup.users);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RecentGroup group = (RecentGroup)o;
      return Objects.equals(FILTER_VALUES, group.FILTER_VALUES);
    }

    @Override
    public int hashCode() {
      return Objects.hash(FILTER_VALUES);
    }
  }

  private class MyVcsLogUiPropertiesImpl extends VcsLogUiPropertiesImpl {
    private final String myId;

    public MyVcsLogUiPropertiesImpl(String id) {
      super(myAppSettings);
      myId = id;
    }

    @NotNull
    @Override
    public State getState() {
      State state = myState.TAB_STATES.get(myId);
      if (state == null) {
        state = new State();
        myState.TAB_STATES.put(myId, state);
      }
      return state;
    }

    @Override
    public void loadState(@NotNull State state) {
      myState.TAB_STATES.put(myId, state);
    }

    @Override
    public void addRecentlyFilteredUserGroup(@NotNull List<String> usersInGroup) {
      addRecentGroup(usersInGroup, myState.RECENTLY_FILTERED_USER_GROUPS);
    }

    @Override
    public void addRecentlyFilteredBranchGroup(@NotNull List<String> valuesInGroup) {
      addRecentGroup(valuesInGroup, myState.RECENTLY_FILTERED_BRANCH_GROUPS);
    }

    @Override
    @NotNull
    public List<List<String>> getRecentlyFilteredUserGroups() {
      return getRecentGroup(myState.RECENTLY_FILTERED_USER_GROUPS);
    }

    @Override
    @NotNull
    public List<List<String>> getRecentlyFilteredBranchGroups() {
      return getRecentGroup(myState.RECENTLY_FILTERED_BRANCH_GROUPS);
    }
  }
}