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

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.PermanentGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface MainVcsLogUiProperties extends VcsLogUiProperties {

  VcsLogUiProperty<Boolean> SHOW_LONG_EDGES = new VcsLogUiProperty<>("Graph.ShowLongEdges");
  VcsLogUiProperty<PermanentGraph.SortType> BEK_SORT_TYPE = new VcsLogUiProperty<>("Graph.BekSortType");
  VcsLogUiProperty<Boolean> COMPACT_REFERENCES_VIEW = new VcsLogUiProperty<>("Table.CompactReferencesView");
  VcsLogUiProperty<Boolean> SHOW_TAG_NAMES = new VcsLogUiProperty<>("Table.ShowTagNames");
  VcsLogUiProperty<Boolean> TEXT_FILTER_MATCH_CASE = new VcsLogUiProperty<>("TextFilter.MatchCase");
  VcsLogUiProperty<Boolean> TEXT_FILTER_REGEX = new VcsLogUiProperty<>("TextFilter.Regex");
  VcsLogUiProperty<Boolean> SHOW_CHANGES_FROM_PARENTS = new VcsLogUiProperty<>("Changes.ShowChangesFromParents");

  void addRecentlyFilteredUserGroup(@NotNull List<String> usersInGroup);

  void addRecentlyFilteredBranchGroup(@NotNull List<String> valuesInGroup);

  @NotNull
  List<List<String>> getRecentlyFilteredUserGroups();

  @NotNull
  List<List<String>> getRecentlyFilteredBranchGroups();

  void saveFilterValues(@NotNull String filterName, @Nullable List<String> values);

  @Nullable
  List<String> getFilterValues(@NotNull String filterName);

  class VcsLogHighlighterProperty extends VcsLogUiProperty<Boolean> {
    private static final Map<String, VcsLogHighlighterProperty> ourProperties = ContainerUtil.newHashMap();
    @NotNull private final String myId;

    public VcsLogHighlighterProperty(@NotNull String name) {
      super("Highlighter." + name);
      myId = name;
    }

    @NotNull
    public String getId() {
      return myId;
    }

    @NotNull
    public static VcsLogHighlighterProperty get(@NotNull String id) {
      VcsLogHighlighterProperty property = ourProperties.get(id);
      if (property == null) {
        property = new VcsLogHighlighterProperty(id);
        ourProperties.put(id, property);
      }
      return property;
    }
  }
}
