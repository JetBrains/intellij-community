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

import com.intellij.vcs.log.graph.PermanentGraph;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface MainVcsLogUiProperties extends VcsLogUiProperties {

  VcsLogUiProperty<Boolean> SHOW_DETAILS = new VcsLogUiProperty<>("Window.ShowDetails");
  VcsLogUiProperty<Boolean> SHOW_LONG_EDGES = new VcsLogUiProperty<>("Graph.ShowLongEdges");
  VcsLogUiProperty<PermanentGraph.SortType> BEK_SORT_TYPE = new VcsLogUiProperty<>("Graph.BekSortType");
  VcsLogUiProperty<Boolean> SHOW_ROOT_NAMES = new VcsLogUiProperty<>("Table.ShowRootNames");
  VcsLogUiProperty<Boolean> COMPACT_REFERENCES_VIEW = new VcsLogUiProperty<>("Table.CompactReferencesView");
  VcsLogUiProperty<Boolean> SHOW_TAG_NAMES = new VcsLogUiProperty<>("Table.ShowTagNames");
  VcsLogUiProperty<Boolean> TEXT_FILTER_MATCH_CASE = new VcsLogUiProperty<>("TextFilter.MatchCase");
  VcsLogUiProperty<Boolean> TEXT_FILTER_REGEX = new VcsLogUiProperty<>("TextFilter.Regex");

  void addRecentlyFilteredUserGroup(@NotNull List<String> usersInGroup);

  void addRecentlyFilteredBranchGroup(@NotNull List<String> valuesInGroup);

  @NotNull
  List<List<String>> getRecentlyFilteredUserGroups();

  @NotNull
  List<List<String>> getRecentlyFilteredBranchGroups();

  boolean isHighlighterEnabled(@NotNull String id);

  void enableHighlighter(@NotNull String id, boolean value);

  void saveFilterValues(@NotNull String filterName, @Nullable List<String> values);

  @Nullable
  List<String> getFilterValues(@NotNull String filterName);

  @CalledInAwt
  void addChangeListener(@NotNull VcsLogUiPropertiesListener listener);

  @CalledInAwt
  void removeChangeListener(@NotNull VcsLogUiPropertiesListener listener);

  interface VcsLogUiPropertiesListener {
    <T> void onPropertyChanged(@NotNull VcsLogUiProperty<T> property);

    void onHighlighterChanged();
  }

  interface TextFilterSettings {
    boolean isFilterByRegexEnabled();

    void setFilterByRegexEnabled(boolean enabled);

    boolean isMatchCaseEnabled();

    void setMatchCaseEnabled(boolean enabled);
  }
}
