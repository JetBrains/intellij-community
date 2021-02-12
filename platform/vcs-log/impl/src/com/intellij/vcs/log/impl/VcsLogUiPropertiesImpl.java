// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores UI configuration based on user activity and preferences.
 */
public abstract class VcsLogUiPropertiesImpl<S extends VcsLogUiPropertiesImpl.State>
  implements PersistentStateComponent<S>, MainVcsLogUiProperties {
  private static final Set<VcsLogUiProperties.VcsLogUiProperty> SUPPORTED_PROPERTIES =
    ContainerUtil.newHashSet(CommonUiProperties.SHOW_DETAILS,
                             MainVcsLogUiProperties.SHOW_LONG_EDGES,
                             MainVcsLogUiProperties.BEK_SORT_TYPE,
                             CommonUiProperties.SHOW_ROOT_NAMES,
                             MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES,
                             MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE,
                             MainVcsLogUiProperties.TEXT_FILTER_REGEX);
  private final Set<PropertiesChangeListener> myListeners = new LinkedHashSet<>();
  @NotNull private final VcsLogApplicationSettings myAppSettings;

  public VcsLogUiPropertiesImpl(@NotNull VcsLogApplicationSettings appSettings) {
    myAppSettings = appSettings;
  }

  @NotNull
  @Override
  public abstract S getState();

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T get(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (myAppSettings.exists(property)) {
      return myAppSettings.get(property);
    }
    S state = getState();
    if (property instanceof VcsLogHighlighterProperty) {
      Boolean result = state.HIGHLIGHTERS.get(((VcsLogHighlighterProperty)property).getId());
      if (result == null) return (T)Boolean.TRUE;
      return (T)result;
    }
    if (property instanceof TableColumnWidthProperty) {
      TableColumnWidthProperty tableColumnWidthProperty = (TableColumnWidthProperty)property;
      if (!state.COLUMN_WIDTH.isEmpty()) {
        tableColumnWidthProperty.moveOldSettings(state.COLUMN_WIDTH, state.COLUMN_ID_WIDTH);
        state.COLUMN_WIDTH = new HashMap<>();
      }
      Integer savedWidth = state.COLUMN_ID_WIDTH.get(property.getName());
      if (savedWidth == null) {
        return (T)Integer.valueOf(-1);
      }
      return (T)savedWidth;
    }
    TextFilterSettings filterSettings = getTextFilterSettings();
    return property.match()
      .ifEq(CommonUiProperties.SHOW_DETAILS).then(state.SHOW_DETAILS_IN_CHANGES)
      .ifEq(SHOW_LONG_EDGES).then(state.LONG_EDGES_VISIBLE)
      .ifEq(CommonUiProperties.SHOW_ROOT_NAMES).then(state.SHOW_ROOT_NAMES)
      .ifEq(SHOW_ONLY_AFFECTED_CHANGES).then(state.SHOW_ONLY_AFFECTED_CHANGES)
      .ifEq(BEK_SORT_TYPE).thenGet(() -> PermanentGraph.SortType.values()[state.BEK_SORT_TYPE])
      .ifEq(TEXT_FILTER_MATCH_CASE).then(filterSettings.MATCH_CASE)
      .ifEq(TEXT_FILTER_REGEX).then(filterSettings.REGEX)
      .get();
  }

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
    else if (CommonUiProperties.SHOW_ROOT_NAMES.equals(property)) {
      getState().SHOW_ROOT_NAMES = (Boolean)value;
    }
    else if (SHOW_ONLY_AFFECTED_CHANGES.equals(property)) {
      getState().SHOW_ONLY_AFFECTED_CHANGES = (Boolean)value;
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
    else if (property instanceof VcsLogHighlighterProperty) {
      getState().HIGHLIGHTERS.put(((VcsLogHighlighterProperty)property).getId(), (Boolean)value);
    }
    else if (property instanceof TableColumnWidthProperty) {
      getState().COLUMN_ID_WIDTH.put(property.getName(), (Integer)value);
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    onPropertyChanged(property);
  }

  protected <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    myListeners.forEach(l -> l.onPropertyChanged(property));
  }

  @Override
  public <T> boolean exists(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (myAppSettings.exists(property) ||
        SUPPORTED_PROPERTIES.contains(property) ||
        property instanceof VcsLogHighlighterProperty ||
        property instanceof TableColumnWidthProperty) {
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

  public static class State {
    public boolean SHOW_DETAILS_IN_CHANGES = true;
    public boolean LONG_EDGES_VISIBLE = false;
    public int BEK_SORT_TYPE = 0;
    public boolean SHOW_ROOT_NAMES = false;
    public boolean SHOW_ONLY_AFFECTED_CHANGES = false;
    public Map<String, Boolean> HIGHLIGHTERS = new TreeMap<>();
    public Map<String, List<String>> FILTERS = new TreeMap<>();
    public TextFilterSettings TEXT_FILTER_SETTINGS = new TextFilterSettings();
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
    public Map<Integer, Integer> COLUMN_WIDTH = new HashMap<>();
    public Map<String, Integer> COLUMN_ID_WIDTH = new HashMap<>();
  }

  public static class TextFilterSettings {
    public boolean REGEX = false;
    public boolean MATCH_CASE = false;
  }
}
