// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.util.registry.Registry
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitWorkingTree
import git4idea.repo.GitRepository

internal object GitWorkingTreesUtil {
  internal const val TOOLWINDOW_TAB_ID: String = "Working Trees"

  fun isWorkingTreesFeatureEnabled(): Boolean {
    return Registry.`is`("git.enable.working.trees.feature", false)
  }

  /**
   * For now only local branches are supported for working trees.
   */
  fun getWorkingTreeWithRef(reference: GitReference, repository: GitRepository, skipCurrentWorkingTree: Boolean): GitWorkingTree? {
    if (!isWorkingTreesFeatureEnabled() || reference !is GitLocalBranch) {
      return null
    }
    return repository.workingTreeHolder.getWorkingTrees().find {
      (!skipCurrentWorkingTree || !it.isCurrent) && it.currentBranch == reference
    }
  }
}