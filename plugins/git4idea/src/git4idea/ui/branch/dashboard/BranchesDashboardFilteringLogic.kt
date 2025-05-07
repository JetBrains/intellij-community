// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import org.jetbrains.annotations.ApiStatus

/**
 * Expected to be overridden only in the workspace model plugin.
 */
@ApiStatus.Internal
interface BranchesDashboardFilteringLogic {
  /**
   * `true` means that branches in the branch dashboard tree will be hidden for roots excluded in the root filter
   */
  fun showBranchesMatchingRootFilter(): Boolean

  /**
   * Defines filtering logic in case of grouping by repository is enabled.
   *
   * `true` means that log will be filtered by both branch name and root, `false` - only by branch name.
   */
  fun filterByBranchAndRoot(): Boolean
}

internal class DefaultBranchDashboardFilteringLogic : BranchesDashboardFilteringLogic {
  override fun showBranchesMatchingRootFilter(): Boolean = true
  override fun filterByBranchAndRoot(): Boolean = !showBranchesMatchingRootFilter()
}