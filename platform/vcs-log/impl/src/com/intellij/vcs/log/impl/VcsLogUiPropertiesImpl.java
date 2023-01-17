// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.PermanentGraph;
import com.intellij.vcs.log.ui.table.column.TableColumnWidthProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores UI configuration based on user activity and preferences.
 */
public abstract class VcsLogUiPropertiesImpl<S extends VcsLogUiPropertiesImpl.State> implements MainVcsLogUiProperties {
  private static final Set<VcsLogUiProperties.VcsLogUiProperty<?>> SUPPORTED_PROPERTIES =
    ContainerUtil.newHashSet(CommonUiProperties.SHOW_DETAILS,
                             MainVcsLogUiProperties.SHOW_LONG_EDGES,
                             MainVcsLogUiProperties.BEK_SORT_TYPE,
                             CommonUiProperties.SHOW_ROOT_NAMES,
                             MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES,
                             MainVcsLogUiProperties.TEXT_FILTER_MATCH_CASE,
                             MainVcsLogUiProperties.TEXT_FILTER_REGEX);
  private final @NotNull EventDispatcher<PropertiesChangeListener> myEventDispatcher = EventDispatcher.create(PropertiesChangeListener.class);
  private final @NotNull VcsLogApplicationSettings myAppSettings;

  public VcsLogUiPropertiesImpl(@NotNull VcsLogApplicationSettings appSettings) {
    myAppSettings = appSettings;
  }

  protected abstract @NotNull S getLogUiState();

  @SuppressWarnings("unchecked")
  @Override
  public @NotNull <T> T get(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    if (myAppSettings.exists(property)) {
      return myAppSettings.get(property);
    }
    S state = getLogUiState();
    if (property instanceof VcsLogHighlighterProperty) {
      Boolean result = state.HIGHLIGHTERS.get(((VcsLogHighlighterProperty)property).getId());
      if (result == null) return (T)Boolean.TRUE;
      return (T)result;
    }
    if (property instanceof TableColumnWidthProperty) {
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
      getLogUiState().SHOW_DETAILS_IN_CHANGES = (Boolean)value;
    }
    else if (SHOW_LONG_EDGES.equals(property)) {
      getLogUiState().LONG_EDGES_VISIBLE = (Boolean)value;
    }
    else if (CommonUiProperties.SHOW_ROOT_NAMES.equals(property)) {
      getLogUiState().SHOW_ROOT_NAMES = (Boolean)value;
    }
    else if (SHOW_ONLY_AFFECTED_CHANGES.equals(property)) {
      getLogUiState().SHOW_ONLY_AFFECTED_CHANGES = (Boolean)value;
    }
    else if (BEK_SORT_TYPE.equals(property)) {
      getLogUiState().BEK_SORT_TYPE = ((PermanentGraph.SortType)value).ordinal();
    }
    else if (TEXT_FILTER_REGEX.equals(property)) {
      getTextFilterSettings().REGEX = (boolean)(Boolean)value;
    }
    else if (TEXT_FILTER_MATCH_CASE.equals(property)) {
      getTextFilterSettings().MATCH_CASE = (boolean)(Boolean)value;
    }
    else if (property instanceof VcsLogHighlighterProperty) {
      getLogUiState().HIGHLIGHTERS.put(((VcsLogHighlighterProperty)property).getId(), (Boolean)value);
    }
    else if (property instanceof TableColumnWidthProperty) {
      getLogUiState().COLUMN_ID_WIDTH.put(property.getName(), (Integer)value);
    }
    else {
      throw new UnsupportedOperationException("Property " + property + " does not exist");
    }
    onPropertyChanged(property);
  }

  protected <T> void onPropertyChanged(@NotNull VcsLogUiProperties.VcsLogUiProperty<T> property) {
    myEventDispatcher.getMulticaster().onPropertyChanged(property);
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

  private @NotNull TextFilterSettings getTextFilterSettings() {
    TextFilterSettings settings = getLogUiState().TEXT_FILTER_SETTINGS;
    if (settings == null) {
      settings = new TextFilterSettings();
      getLogUiState().TEXT_FILTER_SETTINGS = settings;
    }
    return settings;
  }

  @Override
  public void saveFilterValues(@NotNull String filterName, @Nullable List<String> values) {
    if (values != null) {
      getLogUiState().FILTERS.put(filterName, values);
    }
    else {
      getLogUiState().FILTERS.remove(filterName);
    }
  }

  @Override
  public @Nullable List<String> getFilterValues(@NotNull String filterName) {
    return getLogUiState().FILTERS.get(filterName);
  }

  @Override
  public void addChangeListener(@NotNull PropertiesChangeListener listener) {
    myEventDispatcher.addListener(listener);
    myAppSettings.addChangeListener(listener);
  }

  @Override
  public void removeChangeListener(@NotNull PropertiesChangeListener listener) {
    myEventDispatcher.removeListener(listener);
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
    public Map<String, Integer> COLUMN_ID_WIDTH = new HashMap<>();
  }

  public static class TextFilterSettings {
    public boolean REGEX = false;
    public boolean MATCH_CASE = false;
  }
}
