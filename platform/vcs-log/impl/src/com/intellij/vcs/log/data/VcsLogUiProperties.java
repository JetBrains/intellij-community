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

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface VcsLogUiProperties {
  boolean isShowDetails();

  void setShowDetails(boolean showDetails);

  void addRecentlyFilteredUserGroup(@NotNull List<String> usersInGroup);

  void addRecentlyFilteredBranchGroup(@NotNull List<String> valuesInGroup);

  @NotNull
  List<List<String>> getRecentlyFilteredUserGroups();

  @NotNull
  List<List<String>> getRecentlyFilteredBranchGroups();

  boolean areLongEdgesVisible();

  void setLongEdgesVisibility(boolean visible);

  int getBekSortType();

  void setBek(int bekSortType);

  boolean isShowRootNames();

  void setShowRootNames(boolean isShowRootNames);

  boolean isHighlighterEnabled(@NotNull String id);

  void enableHighlighter(@NotNull String id, boolean value);

  boolean isShowBranchesPanel();

  void setShowBranchesPanel(boolean show);
}
