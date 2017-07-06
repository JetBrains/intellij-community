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
  private static final Set<VcsLogUiProperties.VcsLogUiProperty> SUPPORTED_PROPERTIES =
    ContainerUtil.newHashSet(CommonUiProperties.SHOW_DETAILS,
                             MainVcsLogUiProperties.SHOW_LONG_EDGES,
                             MainVcsLogUiProperties.BEK_SORT_TYPE,
                             MainVcsLogUiProperties.SHOW_ROOT_NAMES,
                             MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE,
                             MainVcsLogUiProperties.TEXT_FILTER_REGEX,
                             CommonUiProperties.COLUMN_ORDER);
  private final Set<PropertiesChangeListener> myListeners = ContainerUtil.newLinkedHashSet();
  @NotNull private final VcsLogApplicationSettings myAppSettings;

  public VcsLogUiPropertiesImpl(@NotNull VcsLogApplicationSettings appSettings) {
    myAppSettings = appSettings;
  }

  public static class State {
    public boolean SHOW_DETAILS_IN_CHANGES = true;
    public boolean LONG_EDGES_VISIBLE = false;
    public int BEK_SORT_TYPE = 0;
    public boolean SHOW_ROOT_NAMES = false;
    public Deque<UserGroup> RECENTLY_FILTERED_USER_GROUPS = new ArrayDeque<>();
    public Deque<UserGroup> RECENTLY_FILTERED_BRANCH_GROUPS = new ArrayDeque<>();
    public Map<String, Boolean> HIGHLIGHTERS = ContainerUtil.newTreeMap();
    public Map<String, List<String>> FILTERS = ContainerUtil.newTreeMap();
    @Deprecated public boolean COMPACT_REFERENCES_VIEW = true;
    @Deprecated public boolean SHOW_TAG_NAMES = false;
    public TextFilterSettings TEXT_FILTER_SETTINGS = new TextFilterSettings();
    public Map<Integer, Integer> COLUMN_WIDTH = ContainerUtil.newHashMap();
    public List<Integer> COLUMN_ORDER = ContainerUtil.newArrayList();
  }

  @NotNull
  @Override
  public abstract State getState();

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (myAppSettings.exists(property)) {
      return myAppSettings.get(property);
    }

    if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_DETAILS_IN_CHANGES);
    }
    else if (SHOW_LONG_EDGES.equals(property)) {
      return (T)Boolean.valueOf(getState().LONG_EDGES_VISIBLE);
    }
    else if (SHOW_ROOT_NAMES.equals(property)) {
      return (T)Boolean.valueOf(getState().SHOW_ROOT_NAMES);
    }
    else if (BEK_SORT_TYPE.equals(property)) {
      return (T)PermanentGraph.SortType.values()[getState().BEK_SORT_TYPE];
    }
    else if (TEXT_FILTER_MATCH_CASE.equals(property)) {
      return (T)Boolean.valueOf(getTextFilterSettings().MATCH_CASE);
    }
    else if (TEXT_FILTER_REGEX.equals(property)) {
      return (T)Boolean.valueOf(getTextFilterSettings().REGEX);
    }
    else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
      List<Integer> order = getState().COLUMN_ORDER;
      if (order == null) order = ContainerUtil.newArrayList();
      return (T)order;
    }
    else if (property instanceof VcsLogHighlighterProperty) {
      Boolean result = getState().HIGHLIGHTERS.get(((VcsLogHighlighterProperty)property).getId());
      if (result == null) return (T)Boolean.TRUE;
      return (T)result;
    }
    else if (property instanceof CommonUiProperties.TableColumnProperty) {
      Integer savedWidth = getState().COLUMN_WIDTH.get(((CommonUiProperties.TableColumnProperty)property).getColumn());
      if (savedWidth == null) return (T)Integer.valueOf(-1);
      return (T)savedWidth;
    }
    throw new UnsupportedOperationException("Property " + property + " does not exist");
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> void set(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property, @NotNull T value) {
    if (myAppSettings.exists(property)) {
      myAppSettings.set(property, value);
      return;
    }

    if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
      getState().SHOW_DETAILS_IN_CHANGES = (Boolean)value;
    }
    else if (SHOW_LONG_EDGES.equals(property)) {
      getState().LONG_EDGES_VISIBLE = (Boolean)value;
    }
    else if (SHOW_ROOT_NAMES.equals(property)) {
      getState().SHOW_ROOT_NAMES = (Boolean)value;
    }
    else if (BEK_SORT_TYPE.equals(property)) {
      getState().BEK_SORT_TYPE = ((PermanentGraph.SortType)value).ordinal();
    }
    else if (TEXT_FILTER_REGEX.equals(property)) {
      getTextFilterSettings().REGEX = (boolean)(Boolean)value;
    }
    else if (TEXT_FILTER_MATCH_CASE.equals(property)) {
      getTextFilterSettings().MATCH_CASE = (boolean)(Boolean)value;
    }
    else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
      getState().COLUMN_ORDER = (List<Integer>)value;
    }
    else if (property instanceof VcsLogHighlighterProperty) {
      getState().HIGHLIGHTERS.put(((VcsLogHighlighterProperty)property).getId(), (Boolean)value);
    }
    else if (property instanceof CommonUiProperties.TableColumnProperty) {
      getState().COLUMN_WIDTH.put(((CommonUiProperties.TableColumnProperty)property).getColumn(), (Integer)value);
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (myAppSettings.exists(property) ||
        SUPPORTED_PROPERTIES.contains(property) ||
        property instanceof VcsLogHighlighterProperty ||
        property instanceof CommonUiProperties.TableColumnProperty) {
      return true;
    }
    return false;
  }

  @NotNull
  private TextFilterSettings getTextFilterSettings() {
    TextFilterSettings settings = getState().TEXT_FILTER_SETTINGS;
    if (settings == null) {
      settings = new TextFilterSettings();
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
  public void addChangeListener(@NotNull PropertiesChangeListener listener) {
    myListeners.add(listener);
    myAppSettings.addChangeListener(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertiesChangeListener listener) {
    myListeners.remove(listener);
    myAppSettings.removeChangeListener(listener);
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

  private static class TextFilterSettings {
    public boolean REGEX = false;
    public boolean MATCH_CASE = false;
  }

  public abstract static class MainVcsLogUiPropertiesListener implements PropertiesChangeListener {
    public abstract void onShowDetailsChanged();

    public abstract void onShowLongEdgesChanged();

    public abstract void onBekChanged();

    public abstract void onShowRootNamesChanged();

    public abstract void onCompactReferencesViewChanged();

    public abstract void onShowTagNamesChanged();

    public abstract void onTextFilterSettingsChanged();

    public abstract void onHighlighterChanged();

    public abstract void onColumnWidthChanged(int column);

    public abstract void onColumnOrderChanged();

    @Override
    public <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
      if (CommonUiProperties.SHOW_DETAILS.equals(property)) {
        onShowDetailsChanged();
      }
      else if (SHOW_LONG_EDGES.equals(property)) {
        onShowLongEdgesChanged();
      }
      else if (SHOW_ROOT_NAMES.equals(property)) {
        onShowRootNamesChanged();
      }
      else if (COMPACT_REFERENCES_VIEW.equals(property)) {
        onCompactReferencesViewChanged();
      }
      else if (SHOW_TAG_NAMES.equals(property)) {
        onShowTagNamesChanged();
      }
      else if (BEK_SORT_TYPE.equals(property)) {
        onBekChanged();
      }
      else if (TEXT_FILTER_REGEX.equals(property) || TEXT_FILTER_MATCH_CASE.equals(property)) {
        onTextFilterSettingsChanged();
      }
      else if (CommonUiProperties.COLUMN_ORDER.equals(property)) {
        onColumnOrderChanged();
      }
      else if (property instanceof VcsLogHighlighterProperty) {
        onHighlighterChanged();
      }
      else if (property instanceof CommonUiProperties.TableColumnProperty) {
        onColumnWidthChanged(((CommonUiProperties.TableColumnProperty)property).getColumn());
      }
      else {
        throw new UnsupportedOperationException("Property " + property + " does not exist");
      }
    }
  }
}
