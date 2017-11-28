/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.graph;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * PermanentGraph is created once per repository, and forever until the log is refreshed. <br/>
 * An instance can be created by {@link PermanentGraphBuilder}. <br/>
 * This graph contains all commits in the log and may occupy a lot of memory.
 *
 * @see VisibleGraph
 */
public interface PermanentGraph<Id> {

  /**
   * Create a new instance of VisibleGraph with specific sort type, visible branches and commits.
   *
   * @param sortType               mechanism of sorting for commits in the graph (see {@link PermanentGraph.SortType}):
   *                               <ul><li/> sort topologically and by date,
   *                               <li/> show incoming commits first for merges (IntelliSort),
   *                               <li/> show incoming commits on top of main branch commits as if they were rebased (linear IntelliSort).</ul>
   * @param headsOfVisibleBranches heads of visible branches, null value means all branches are visible.
   * @param matchedCommits         visible commits, null value means all commits are visible.
   * @return new instance of VisibleGraph.
   */
  @NotNull
  VisibleGraph<Id> createVisibleGraph(@NotNull SortType sortType,
                                      @Nullable Set<Id> headsOfVisibleBranches,
                                      @Nullable Set<Id> matchedCommits);

  @NotNull
  List<GraphCommit<Id>> getAllCommits();

  @NotNull
  List<Id> getChildren(@NotNull Id commit);

  @NotNull
  Set<Id> getContainingBranches(@NotNull Id commit);

  @NotNull
  Condition<Id> getContainedInBranchCondition(@NotNull Collection<Id> currentBranchHead);

  enum SortType {
    Normal("Off", "Sort commits topologically and by date"),
    Bek("Standard", "In case of merge show incoming commits first (directly below merge commit)"),
    LinearBek("Linear", "In case of merge show incoming commits on top of main branch commits as if they were rebased");

    @NotNull private final String myPresentation;
    @NotNull private final String myDescription;

    SortType(@NotNull String presentation, @NotNull String description) {
      myPresentation = presentation;
      myDescription = description;
    }

    @NotNull
    public String getName() {
      return myPresentation;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }
  }
}
