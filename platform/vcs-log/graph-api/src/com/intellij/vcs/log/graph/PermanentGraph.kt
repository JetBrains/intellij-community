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
package com.intellij.vcs.log.graph

import java.util.function.Predicate

/**
 * PermanentGraph is created once per repository, and forever until the log is refreshed.
 *
 * This graph contains all commits in the log and may occupy a lot of memory.
 *
 * @see VisibleGraph
 */
interface PermanentGraph<Id> {
  /**
   * Create a new instance of VisibleGraph with a specific sort type, visible branches, and commits.
   *
   * @param sortType               mechanism of sorting for commits in the graph (see [SortType]):
   *                               * sort topologically and by date,
   *                               * show incoming commits first for merges (IntelliSort),
   *                               * show incoming commits on top of main branch commits as if they were rebased (linear IntelliSort).
   * @param headsOfVisibleBranches heads of visible branches, null value means all branches are visible.
   * @param matchedCommits         visible commits, null value means all commits are visible.
   * @return new instance of VisibleGraph.
   */
  fun createVisibleGraph(sortType: SortType,
                         headsOfVisibleBranches: Set<Id>?,
                         matchedCommits: Set<Id>?): VisibleGraph<Id>

  val allCommits: List<GraphCommit<Id>>

  fun getChildren(commit: Id): List<Id>

  fun getContainingBranches(commit: Id): Set<Id>

  fun getContainedInBranchCondition(currentBranchHead: Collection<Id>): Predicate<Id>

  enum class SortType(val presentation: String, val description: String) {
    Normal("Off", "Sort commits topologically and by date"),  // NON-NLS
    Bek("Standard", "In case of merge show incoming commits first (directly below merge commit)"),  // NON-NLS
    LinearBek("Linear", "In case of merge show incoming commits on top of main branch commits as if they were rebased") // NON-NLS
  }
}
