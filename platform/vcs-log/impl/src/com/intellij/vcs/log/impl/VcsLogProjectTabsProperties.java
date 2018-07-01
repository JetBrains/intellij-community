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
import static com.intellij.vcs.log.ui.filter.BranchFilterPopupComponent.BRANCH_FILTER_NAME;
import static com.intellij.vcs.log.ui.filter.UserFilterPopupComponent.USER_FILER_NAME;
import static java.util.Comparator.comparingInt;

@State(name = "Vcs.Log.Tabs.Properties", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class VcsLogProjectTabsProperties implements PersistentStateComponent<VcsLogProjectTabsProperties.State>, VcsLogTabsProperties {
  public static final String MAIN_LOG_ID = "MAIN";
  private static final int RECENTLY_FILTERED_VALUES_LIMIT = 10;
  @NotNull private final VcsLogApplicationSettings myAppSettings;
  private State myState = new State();

  public VcsLogProjectTabsProperties(@NotNull VcsLogApplicationSettings appSettings) {
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
    if (isEmpty(myState.RECENT_FILTERS)) {

      myState.RECENT_FILTERS = newHashMap();

      Multiset<RecentGroup> branchFrequencies = HashMultiset.create();
      Multiset<RecentGroup> userFrequencies = HashMultiset.create();
      for (Map.Entry<String, VcsLogUiPropertiesImpl.State> entry : myState.TAB_STATES.entrySet()) {
        if (entry.getKey().startsWith("EXTERNAL")) continue; // do not migrate recent items for external logs
        VcsLogUiPropertiesImpl.State s = entry.getValue();
        branchFrequencies.addAll(map(s.RECENTLY_FILTERED_BRANCH_GROUPS, RecentGroup::new));
        userFrequencies.addAll(map(s.RECENTLY_FILTERED_USER_GROUPS, RecentGroup::new));
        s.RECENTLY_FILTERED_BRANCH_GROUPS.clear();
        s.RECENTLY_FILTERED_USER_GROUPS.clear();
      }

      List<RecentGroup> sortedBranches = sorted(branchFrequencies.elementSet(), comparingInt(value -> -branchFrequencies.count(value)));
      List<RecentGroup> sortedUsers = sorted(userFrequencies.elementSet(), comparingInt(value -> -userFrequencies.count(value)));

      myState.RECENT_FILTERS.put(BRANCH_FILTER_NAME, newArrayList(getFirstItems(sortedBranches, RECENTLY_FILTERED_VALUES_LIMIT)));
      myState.RECENT_FILTERS.put(USER_FILER_NAME, newArrayList(getFirstItems(sortedUsers, RECENTLY_FILTERED_VALUES_LIMIT)));
    }
  }

  /**
   * Method for migrating external log tabs properties to the other storage.
   * Finds a state by id and removes it.
   * To be removed after 2018.3 release.
   */
  @Deprecated
  @Nullable
  public VcsLogUiPropertiesImpl.State removeTabState(@NotNull String id) {
    return myState.TAB_STATES.remove(id);
  }

  @Override
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

  public static void addRecentGroup(@NotNull Map<String, List<RecentGroup>> stateField,
                                    @NotNull String filterName,
                                    @NotNull Collection<String> values) {
    List<RecentGroup> recentGroups = stateField.get(filterName);
    if (recentGroups == null) {
      recentGroups = newArrayList();
      stateField.put(filterName, recentGroups);
    }
    RecentGroup group = new RecentGroup(values);
    recentGroups.remove(group);
    recentGroups.add(0, group);
    while (recentGroups.size() > RECENTLY_FILTERED_VALUES_LIMIT) {
      recentGroups.remove(recentGroups.size() - 1);
    }
  }

  @NotNull
  public static List<List<String>> getRecentGroup(@NotNull Map<String, List<RecentGroup>> stateField, @NotNull String filterName) {
    List<RecentGroup> values = stateField.get(filterName);
    if (values == null) {
      return emptyList();
    }
    return map2List(values, group -> group.FILTER_VALUES);
  }

  public static class State {
    public Map<String, VcsLogUiPropertiesImpl.State> TAB_STATES = newTreeMap();
    public LinkedHashSet<String> OPEN_TABS = newLinkedHashSet();
    public Map<String, List<RecentGroup>> RECENT_FILTERS = newHashMap();
  }

  public static class RecentGroup {
    @XCollection
    public List<String> FILTER_VALUES = newArrayList();

    public RecentGroup() {
    }

    public RecentGroup(@NotNull Collection<String> values) {
      FILTER_VALUES.addAll(values);
    }

    public RecentGroup(@NotNull VcsLogUiPropertiesImpl.UserGroup oldGroup) {
      this(oldGroup.users);
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

  private class MyVcsLogUiPropertiesImpl extends VcsLogUiPropertiesImpl<VcsLogUiPropertiesImpl.State> {
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
    public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
      addRecentGroup(myState.RECENT_FILTERS, filterName, values);
    }

    @NotNull
    @Override
    public List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
      return getRecentGroup(myState.RECENT_FILTERS, filterName);
    }
  }
}