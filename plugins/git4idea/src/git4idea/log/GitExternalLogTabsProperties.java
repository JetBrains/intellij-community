// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XMap;
import com.intellij.vcs.log.impl.*;
import com.intellij.vcs.log.impl.VcsLogProjectTabsProperties.RecentGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.vcs.log.ui.filter.BranchFilterPopupComponent.BRANCH_FILTER_NAME;
import static com.intellij.vcs.log.ui.filter.UserFilterPopupComponent.USER_FILER_NAME;

@State(
  name = "Git.Log.External.Tabs.Properties",
  storages = {
    @Storage(value = "git.external.log.tabs.xml", roamingType = RoamingType.DISABLED)
  }
)
public class GitExternalLogTabsProperties implements PersistentStateComponent<GitExternalLogTabsProperties.State>, VcsLogTabsProperties {
  @NotNull private final VcsLogApplicationSettings myAppSettings;
  @NotNull private final ProjectManager myProjectManager;
  private State myState = new State();

  public GitExternalLogTabsProperties(@NotNull VcsLogApplicationSettings settings, @NotNull ProjectManager projectManager) {
    myAppSettings = settings;
    myProjectManager = projectManager;
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
    if (!myState.TAB_STATES.containsKey(id)) {
      myState.TAB_STATES.put(id, createOrMigrateTabState(id));
    }
    return new MyVcsLogUiProperties(id);
  }

  @NotNull
  private TabState createOrMigrateTabState(@NotNull String id) {
    // migration from VcsLogProjectTabsProperties, to remove after 2018.3 release
    Project[] projects = myProjectManager.getOpenProjects();
    for (Project project : projects) {
      VcsLogProjectTabsProperties projectTabsProperties = ServiceManager.getServiceIfCreated(project, VcsLogProjectTabsProperties.class);
      if (projectTabsProperties != null) {
        VcsLogUiPropertiesImpl.State oldState = projectTabsProperties.removeTabState(id);
        if (oldState != null) {
          TabState newState = new TabState();
          newState.SHOW_DETAILS_IN_CHANGES = oldState.SHOW_DETAILS_IN_CHANGES;
          newState.LONG_EDGES_VISIBLE = oldState.LONG_EDGES_VISIBLE;
          newState.BEK_SORT_TYPE = oldState.BEK_SORT_TYPE;
          newState.SHOW_ROOT_NAMES = oldState.SHOW_ROOT_NAMES;
          List<RecentGroup> recentBranches = ContainerUtil.map2List(oldState.RECENTLY_FILTERED_BRANCH_GROUPS, RecentGroup::new);
          List<RecentGroup> recentUsers = ContainerUtil.map2List(oldState.RECENTLY_FILTERED_USER_GROUPS, RecentGroup::new);
          newState.RECENT_FILTERS.put(BRANCH_FILTER_NAME, recentBranches);
          newState.RECENT_FILTERS.put(USER_FILER_NAME, recentUsers);
          newState.HIGHLIGHTERS.putAll(oldState.HIGHLIGHTERS);
          newState.FILTERS.putAll(oldState.FILTERS);
          newState.COLUMN_WIDTH.putAll(oldState.COLUMN_WIDTH);
          newState.COLUMN_ORDER.addAll(oldState.COLUMN_ORDER);
          newState.TEXT_FILTER_SETTINGS.MATCH_CASE = oldState.TEXT_FILTER_SETTINGS.MATCH_CASE;
          newState.TEXT_FILTER_SETTINGS.REGEX = oldState.TEXT_FILTER_SETTINGS.REGEX;
          return newState;
        }
      }
    }
    return new TabState();
  }

  public static class State {
    @XMap
    public Map<String, TabState> TAB_STATES = ContainerUtil.newTreeMap();
  }

  public static class TabState extends VcsLogUiPropertiesImpl.State {
    @XCollection
    public Map<String, List<RecentGroup>> RECENT_FILTERS = ContainerUtil.newHashMap();
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
