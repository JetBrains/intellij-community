// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties.RecentGroup;
import com.intellij.vcs.log.util.FilterConfigMigrationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "Git.Log.External.Tabs.Properties", storages = @Storage(value = "git.external.log.tabs.xml", roamingType = RoamingType.DISABLED))
public final class GitExternalLogTabsProperties
  implements PersistentStateComponent<GitExternalLogTabsProperties.State>, VcsLogTabsProperties {
  private State myState = new State();

  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;

    if (!myState.oldMeFiltersMigrated) {
      myState.TAB_STATES.values().forEach(tabState -> {
        FilterConfigMigrationUtil.migrateRecentUserFilters(tabState.RECENT_FILTERS);
        FilterConfigMigrationUtil.migrateTabUserFilters(tabState.FILTERS);
      });
      myState.oldMeFiltersMigrated = true;
    }
  }

  @NotNull
  @Override
  public MainVcsLogUiProperties createProperties(@NotNull String id) {
    if (!myState.TAB_STATES.containsKey(id)) {
      myState.TAB_STATES.put(id, new TabState());
    }
    return new MyVcsLogUiProperties(id);
  }

  public static class State {
    @XMap
    public Map<String, TabState> TAB_STATES = new TreeMap<>();
    public boolean oldMeFiltersMigrated = false;
  }

  public static class TabState extends VcsLogUiPropertiesImpl.State {
    @XCollection
    public Map<String, List<RecentGroup>> RECENT_FILTERS = new HashMap<>();
  }

  private class MyVcsLogUiProperties extends VcsLogUiPropertiesImpl<TabState> {
    @NotNull private final String myId;

    MyVcsLogUiProperties(@NotNull String id) {
      super(ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class));
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
    public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
      VcsLogProjectTabsProperties.addRecentGroup(getState().RECENT_FILTERS, filterName, values);
    }

    @NotNull
    @Override
    public List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
      return VcsLogProjectTabsProperties.getRecentGroup(getState().RECENT_FILTERS, filterName);
    }
  }
}
