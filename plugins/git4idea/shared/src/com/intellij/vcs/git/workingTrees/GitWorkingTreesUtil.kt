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
  fun findCheckedOutWorkingTree(
    reference: GitReference,
    workingTrees: Collection<GitWorkingTree>,
    skipCurrentWorkingTree: Boolean,
  ): GitWorkingTree? {
    if (!isWorkingTreesFeatureEnabled() || reference !is GitStandardLocalBranch) {
      return null
    }

    return workingTrees.find { (!skipCurrentWorkingTree || !it.isCurrent) && it.currentBranch == reference }
  }

  /**
   * Collapses repositories that are actually working trees of the *same* underlying git repository.
   *
   * A repository and each of its linked working trees can independently be registered as VCS roots of a
   * (multi-root) project. They all report the same working-tree list, so, left as-is, the Worktrees view would show
   * one entry per registered root with identical, duplicated worktree lists.
   *
   * Grouping is keyed by [commonGitDirPath] — the git directory shared by all working trees of a repository — which is
   * the only identity that holds for every layout:
   * * a *bare* repository has no main checkout at all, and its administrative entry is not reported as a working tree,
   *   so no registered root reports a [GitWorkingTree.isMain] entry to key on;
   * * for a *submodule*, git reports the main working tree at the submodule's git directory rather than at its
   *   checkout, so the submodule and its linked working trees would disagree about that path.
   *
   * Of each group, the repository rooted at the main working tree (the main checkout) survives when it is registered;
   * otherwise — a bare repository, or a project that registered only linked working trees — the root that sorts first
   * does, so that the choice is stable across sessions. Genuinely distinct repositories (peers, submodules, plain
   * nested repositories) have distinct git directories and are left untouched.
   */
  fun <T> mergeLinkedWorktreeRepositories(
    repositories: List<T>,
    rootPath: (T) -> String,
    commonGitDirPath: (T) -> String,
    workingTrees: (T) -> Collection<GitWorkingTree>,
  ): List<T> =
    repositories
      .groupBy(commonGitDirPath)
      .map { (_, group) ->
        // The main checkout is the root whose own working tree is the main one; a bare repository has no such root.
        group.firstOrNull { repository -> workingTrees(repository).any { it.isCurrent && it.isMain } }
        ?: group.minBy(rootPath)
      }
}