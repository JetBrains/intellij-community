// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.workingTrees

import com.intellij.openapi.util.registry.Registry
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

  /**
   * Finds the linked working tree that currently has [reference] checked out among [workingTrees], or `null`
   * if none does. Only local branches are matched: a worktree checks out exactly one local branch, while a
   * remote branch / tag / detached HEAD never blocks reuse.
   *
   * @param skipCurrentWorkingTree exclude the worktree the caller is already in (its "current" one).
   */
  fun findCheckedOutWorkingTree(reference: GitReference, workingTrees: Collection<GitWorkingTree>, skipCurrentWorkingTree: Boolean, ): GitWorkingTree? {
    if (!isWorkingTreesFeatureEnabled() || reference !is GitStandardLocalBranch) {
      return null
    }

    return workingTrees.find { (!skipCurrentWorkingTree || !it.isCurrent) && it.currentBranch == reference }
  }

  /**
   * Collapses repositories that are actually linked working trees of the *same* underlying git repository.
   *
   * A repository and each of its linked working trees can independently be registered as VCS roots of a
   * (multi-root) project. They all report the same working-tree list, and the same **primary** (main) working
   * tree — so, left as-is, the Worktrees view would show one entry per registered root with identical,
   * duplicated worktree lists.
   *
   * This keeps a single repository per underlying git repository, keyed by the primary working-tree path,
   * preferring the repository that is rooted at that primary working tree (the main checkout) when present.
   * Repositories whose working trees are not loaded yet, or that are genuinely distinct (peers, submodules,
   * plain nested repositories), have distinct keys and are left untouched.
   */
  fun <T> mergeLinkedWorktreeRepositories(
    repositories: List<T>,
    rootPath: (T) -> String,
    workingTrees: (T) -> Collection<GitWorkingTree>,
  ): List<T> =
    repositories
      .groupBy { repository -> workingTrees(repository).firstOrNull { it.isMain }?.path?.path ?: rootPath(repository) }
      .map { (primaryPath, group) -> group.firstOrNull { rootPath(it) == primaryPath } ?: group.first() }
}