// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.workingTrees

import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitReference
import git4idea.GitStandardLocalBranch
import git4idea.GitWorkingTree
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
object GitWorkingTreesUtil {
  const val TOOLWINDOW_TAB_ID: @NonNls String = "Working Trees"

  fun isWorkingTreesFeatureEnabled(): Boolean {
    return Registry.`is`("git.enable.working.trees.feature", false)
  }

  fun getWorkingTreeWithRef(reference: GitReference, repository: GitRepositoryModel, skipCurrentWorkingTree: Boolean): GitWorkingTree? {
    return getWorkingTreeWithRef(reference, repository, skipCurrentWorkingTree) {
      repository.state.workingTrees
    }
  }

  /**
   * For now only local branches are supported for working trees.
   */
  fun <T> getWorkingTreeWithRef(
    reference: GitReference,
    repository: T,
    skipCurrentWorkingTree: Boolean,
    workingTrees: (T) -> Collection<GitWorkingTree>,
  ): GitWorkingTree? {
    if (!isWorkingTreesFeatureEnabled() || reference !is GitStandardLocalBranch) {
      return null
    }
    val workingTrees = workingTrees(repository)
    return workingTrees.find {
      (!skipCurrentWorkingTree || !it.isCurrent) && it.currentBranch == reference
    }
  }
}