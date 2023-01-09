// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.graph.PermanentGraph;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface MainVcsLogUiProperties extends VcsLogUiProperties {

  VcsLogUiProperty<Boolean> SHOW_LONG_EDGES = new VcsLogUiProperty<>("Graph.ShowLongEdges");
  VcsLogUiProperty<PermanentGraph.SortType> BEK_SORT_TYPE = new VcsLogUiProperty<>("Graph.BekSortType");
  VcsLogUiProperty<Boolean> TEXT_FILTER_MATCH_CASE = new VcsLogUiProperty<>("TextFilter.MatchCase");
  VcsLogUiProperty<Boolean> TEXT_FILTER_REGEX = new VcsLogUiProperty<>("TextFilter.Regex");
  VcsLogUiProperty<Boolean> SHOW_CHANGES_FROM_PARENTS = new VcsLogUiProperty<>("Changes.ShowChangesFromParents");
  VcsLogUiProperty<Boolean> SHOW_ONLY_AFFECTED_CHANGES = new VcsLogUiProperty<>("Changes.ShowOnlyAffected");
  VcsLogUiProperty<Boolean> DIFF_PREVIEW_VERTICAL_SPLIT = new VcsLogUiProperty<>("Window.DiffPreviewVerticalSplit");

  void addRecentlyFilteredGroup(@NonNls @NotNull String filterName, @NotNull Collection<String> values);

  @NotNull
  List<List<String>> getRecentlyFilteredGroups(@NonNls @NotNull String filterName);

  void saveFilterValues(@NonNls @NotNull String filterName, @Nullable List<String> values);

  @Nullable
  List<String> getFilterValues(@NotNull String filterName);

  class VcsLogHighlighterProperty extends VcsLogUiProperty<Boolean> {
    private static final Map<String, VcsLogHighlighterProperty> ourProperties = new HashMap<>();
    private final @NotNull String myId;

    public VcsLogHighlighterProperty(@NotNull String name) {
      super("Highlighter." + name);
      myId = name;
    }

    public @NotNull String getId() {
      return myId;
    }

    public static @NotNull VcsLogHighlighterProperty get(@NotNull String id) {
      VcsLogHighlighterProperty property = ourProperties.get(id);
      if (property == null) {
        property = new VcsLogHighlighterProperty(id);
        ourProperties.put(id, property);
      }
      return property;
    }
  }
}
