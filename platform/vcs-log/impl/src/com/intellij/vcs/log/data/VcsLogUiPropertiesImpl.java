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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.PermanentGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores UI configuration based on user activity and preferences.
 */
public abstract class VcsLogUiPropertiesImpl implements PersistentStateComponent<VcsLogUiPropertiesImpl.State>, MainVcsLogUiProperties {
  private static final int RECENTLY_FILTERED_VALUES_LIMIT = 10;
  private final Set<VcsLogUiPropertiesListener> myListeners = ContainerUtil.newLinkedHashSet();

  public static class State {
    public boolean SHOW_DETAILS_IN_CHANGES = true;
    public boolean LONG_EDGES_VISIBLE = false;
    public int BEK_SORT_TYPE = 0;
    public boolean SHOW_ROOT_NAMES = false;
    public Deque<UserGroup> RECENTLY_FILTERED_USER_GROUPS = new ArrayDeque<>();
    public Deque<UserGroup> RECENTLY_FILTERED_BRANCH_GROUPS = new ArrayDeque<>();
    public Map<String, Boolean> HIGHLIGHTERS = ContainerUtil.newTreeMap();
    public Map<String, List<String>> FILTERS = ContainerUtil.newTreeMap();
    public boolean COMPACT_REFERENCES_VIEW = true;
    public boolean SHOW_TAG_NAMES = false;
    public TextFilterSettingsImpl TEXT_FILTER_SETTINGS = new TextFilterSettingsImpl();
  }

  @NotNull
  @Override
  public abstract State getState();

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperty<T> property) {
    if (property == SHOW_DETAILS) {
      return (T)Boolean.valueOf(getState().SHOW_DETAILS_IN_CHANGES);
    }
    else if (property == SHOW_LONG_EDGES) {
      return (T)Boolean.valueOf(getState().LONG_EDGES_VISIBLE);
    }
    else if (property == SHOW_ROOT_NAMES) {
      return (T)Boolean.valueOf(getState().SHOW_ROOT_NAMES);
    }
    else if (property == COMPACT_REFERENCES_VIEW) {
      return (T)Boolean.valueOf(getState().COMPACT_REFERENCES_VIEW);
    }
    else if (property == SHOW_TAG_NAMES) {
      return (T)Boolean.valueOf(getState().SHOW_TAG_NAMES);
    }
    else if (property == BEK_SORT_TYPE) {
      return (T)PermanentGraph.SortType.values()[getState().BEK_SORT_TYPE];
    }
    else if (property == TEXT_FILTER_MATCH_CASE) {
      return (T)Boolean.valueOf(getTextFilterSettings().isMatchCaseEnabled());
    }
    else if (property == TEXT_FILTER_REGEX) {
      return (T)Boolean.valueOf(getTextFilterSettings().isFilterByRegexEnabled());
    }
    throw new UnsupportedOperationException("Property " + property + " does not exist");
  }

  @Override
  public <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value) {
    if (property == SHOW_DETAILS) {
      getState().SHOW_DETAILS_IN_CHANGES = (Boolean)value;
    }
    else if (property == SHOW_LONG_EDGES) {
      getState().LONG_EDGES_VISIBLE = (Boolean)value;
    }
    else if (property == SHOW_ROOT_NAMES) {
      getState().SHOW_ROOT_NAMES = (Boolean)value;
    }
    else if (property == COMPACT_REFERENCES_VIEW) {
      getState().COMPACT_REFERENCES_VIEW = (Boolean)value;
    }
    else if (property == SHOW_TAG_NAMES) {
      getState().SHOW_TAG_NAMES = (Boolean)value;
    }
    else if (property == BEK_SORT_TYPE) {
      getState().BEK_SORT_TYPE = ((PermanentGraph.SortType)value).ordinal();
    }
    else if (property == TEXT_FILTER_REGEX) {
      getTextFilterSettings().setFilterByRegexEnabled((Boolean)value);
    }
    else if (property == TEXT_FILTER_MATCH_CASE) {
      getTextFilterSettings().setMatchCaseEnabled((Boolean)value);
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperty<T> property) {
    if (property == SHOW_DETAILS ||
        property == SHOW_LONG_EDGES ||
        property == SHOW_ROOT_NAMES ||
        property == COMPACT_REFERENCES_VIEW ||
        property == SHOW_TAG_NAMES ||
        property == BEK_SORT_TYPE ||
        property == TEXT_FILTER_MATCH_CASE ||
        property == TEXT_FILTER_REGEX) {
      return true;
    }
    return false;
  }

  @NotNull
  private TextFilterSettingsImpl getTextFilterSettings() {
    TextFilterSettingsImpl settings = getState().TEXT_FILTER_SETTINGS;
    if (settings == null) {
      settings = new TextFilterSettingsImpl();
      getState().TEXT_FILTER_SETTINGS = settings;
    }
    return settings;
  }

  @Override
  public void addRecentlyFilteredUserGroup(@NotNull List<String> usersInGroup) {
    addRecentGroup(usersInGroup, getState().RECENTLY_FILTERED_USER_GROUPS);
  }

  @Override
  public void addRecentlyFilteredBranchGroup(@NotNull List<String> valuesInGroup) {
    addRecentGroup(valuesInGroup, getState().RECENTLY_FILTERED_BRANCH_GROUPS);
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

  @Override
  @NotNull
  public List<List<String>> getRecentlyFilteredUserGroups() {
    return getRecentGroup(getState().RECENTLY_FILTERED_USER_GROUPS);
  }

  @Override
  @NotNull
  public List<List<String>> getRecentlyFilteredBranchGroups() {
    return getRecentGroup(getState().RECENTLY_FILTERED_BRANCH_GROUPS);
  }

  @NotNull
  private static List<List<String>> getRecentGroup(Deque<UserGroup> stateField) {
    return ContainerUtil.map2List(stateField, group -> group.users);
  }

  @Override
  public boolean isHighlighterEnabled(@NotNull String id) {
    Boolean result = getState().HIGHLIGHTERS.get(id);
    return result != null ? result : true; // new highlighters get enabled by default
  }

  @Override
  public void enableHighlighter(@NotNull String id, boolean value) {
    getState().HIGHLIGHTERS.put(id, value);
    myListeners.forEach(VcsLogUiPropertiesListener::onHighlighterChanged);
  }

  @Override
  public void saveFilterValues(@NotNull String filterName, @Nullable List<String> values) {
    if (values != null) {
      getState().FILTERS.put(filterName, values);
    }
    else {
      getState().FILTERS.remove(filterName);
    }
  }

  @Nullable
  @Override
  public List<String> getFilterValues(@NotNull String filterName) {
    return getState().FILTERS.get(filterName);
  }

  @Override
  public void addChangeListener(@NotNull VcsLogUiPropertiesListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(@NotNull VcsLogUiPropertiesListener listener) {
    myListeners.remove(listener);
  }

  public static class UserGroup {
    public List<String> users = new ArrayList<>();

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

  public static class TextFilterSettingsImpl implements TextFilterSettings {
    public boolean REGEX = false;
    public boolean MATCH_CASE = false;

    public TextFilterSettingsImpl(boolean isFilterByRegexEnabled, boolean isMatchCaseEnabled) {
      REGEX = isFilterByRegexEnabled;
      MATCH_CASE = isMatchCaseEnabled;
    }

    public TextFilterSettingsImpl() {
      this(false, false);
    }

    @Override
    public boolean isFilterByRegexEnabled() {
      return REGEX;
    }

    @Override
    public void setFilterByRegexEnabled(boolean enabled) {
      REGEX = enabled;
    }

    @Override
    public boolean isMatchCaseEnabled() {
      return MATCH_CASE;
    }

    @Override
    public void setMatchCaseEnabled(boolean enabled) {
      MATCH_CASE = enabled;
    }
  }

  public abstract static class MainVcsLogUiPropertiesListener implements VcsLogUiPropertiesListener {
    public abstract void onShowDetailsChanged();

    public abstract void onShowLongEdgesChanged();

    public abstract void onBekChanged();

    public abstract void onShowRootNamesChanged();

    public abstract void onCompactReferencesViewChanged();

    public abstract void onShowTagNamesChanged();

    public abstract void onTextFilterSettingsChanged();

    @Override
    public <T> void onPropertyChanged(@NotNull VcsLogUiProperty<T> property) {
      if (property == SHOW_DETAILS) {
        onShowDetailsChanged();
      }
      else if (property == SHOW_LONG_EDGES) {
        onShowLongEdgesChanged();
      }
      else if (property == SHOW_ROOT_NAMES) {
        onShowRootNamesChanged();
      }
      else if (property == COMPACT_REFERENCES_VIEW) {
        onCompactReferencesViewChanged();
      }
      else if (property == SHOW_TAG_NAMES) {
        onShowTagNamesChanged();
      }
      else if (property == BEK_SORT_TYPE) {
        onBekChanged();
      }
      else if (property == TEXT_FILTER_REGEX || property == TEXT_FILTER_MATCH_CASE) {
        onTextFilterSettingsChanged();
      }
      else {
        throw new UnsupportedOperationException("Property " + property + " does not exist");
      }
    }
  }
}
