// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * Create a new instance of VisibleGraph with a specific options, visible branches, and commits.
   *
   * @param options                controls how the graph is created:
   *                               * change sorting of the commits in the graph (see [SortType]),
   *                               * show incoming commits on top of main branch commits as if they were rebased (linear IntelliSort).
   * @param headsOfVisibleBranches heads of visible branches, null value means all branches are visible.
   * @param matchedCommits         visible commits, null value means all commits are visible.
   * @return new instance of VisibleGraph.
   */
  fun createVisibleGraph(options: Options, headsOfVisibleBranches: Set<Id>?, matchedCommits: Set<Id>?): VisibleGraph<Id>

  /**
   * Create a new instance of VisibleGraph with a specific sort type, visible branches, and commits.
   *
   * @param sortType              mechanism of sorting for commits in the graph (see [SortType]):
   *                              * sort topologically and by date,
   *                              * show incoming commits first for merges (IntelliSort),
   * @param headsOfVisibleBranches heads of visible branches, null value means all branches are visible.
   * @param matchedCommits         visible commits, null value means all commits are visible.
   * @return new instance of VisibleGraph.
   */
  fun createVisibleGraph(sortType: SortType, headsOfVisibleBranches: Set<Id>?, matchedCommits: Set<Id>?): VisibleGraph<Id> {
    return createVisibleGraph(Options.Base(sortType), headsOfVisibleBranches, matchedCommits)
  }

  val allCommits: List<GraphCommit<Id>>

  fun getChildren(commit: Id): List<Id>

  fun getContainingBranches(commit: Id): Set<Id>

  fun getContainedInBranchCondition(currentBranchHead: Collection<Id>): Predicate<Id>

  /**
   * Sorting mechanism for the commits in the visible graph.
   */
  enum class SortType(val presentation: String, val description: String) {
    Normal("Off", "Sort commits topologically and by date"),  // NON-NLS
    Bek("Standard", "In case of merge show incoming commits first (directly below merge commit)"),  // NON-NLS
  }

  /**
   * Options, controlling how the visible graph is created.
   */
  sealed class Options {
    /**
     * Show the graph as is, sort commits as specified by the provided [SortType].
     */
    data class Base(val sortType: SortType) : Options()

    /**
     * Modify the graph to show incoming commits on top of main branch commits as if they were rebased.
     */
    data object LinearBek : Options()

    /**
     * Follow only the first parent commit upon seeing a merge commit.
     */
    data object FirstParent: Options()

    companion object {
      @JvmField
      val Default = Base(SortType.Normal)
    }
  }
}