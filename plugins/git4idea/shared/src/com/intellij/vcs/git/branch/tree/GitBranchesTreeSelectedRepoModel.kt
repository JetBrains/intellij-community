// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.vcs.git.branch.popup.GitBranchesPopupBase
import com.intellij.vcs.git.repo.GitRepositoryModel
import javax.swing.tree.TreePath

/**
 * Used if:
 * * "Execute operations on all roots" is disabled
 * * Current repository was defined (see [git4idea.branch.GitBranchUtil.guessWidgetRepository])
 *
 * Besides displaying refs and actions for the selected repo, it also displays a list of all git repos in the project
 */
internal class GitBranchesTreeSelectedRepoModel(
  project: Project,
  selectedRepository: GitRepositoryModel,
  /**
   * Note that it isn't the same as [repositories].
   * [allProjectRepositories] contains a list of repositories to be displayed as repo-level nodes in this tree,
   * while [repositories] is used to specify repositories which refs should be displayed
   */
  private val allProjectRepositories: List<GitRepositoryModel>,
  topLevelActions: List<Any>
) : GitBranchesTreeSingleRepoModel(project, selectedRepository, topLevelActions) {
  private val actionsSeparator = GitBranchesPopupBase.createTreeSeparator()
  private val branchesSubtreeSeparator = GitBranchesPopupBase.createTreeSeparator()

  private lateinit var repositoriesTree: LazyRepositoryHolder

  override fun rebuild(matcher: MinusculeMatcher?) {
    super.rebuild(matcher)
    repositoriesTree = LazyRepositoryHolder(project, allProjectRepositories, matcher, canHaveChildren = false)
  }

  override fun getTopLevelNodes(): List<Any> {
    val matchedActions = actionsTree.match
    val matchedRepos = repositoriesTree.match
    if (matchedRepos.isNotEmpty()) {
      addSeparatorIfNeeded(matchedActions, actionsSeparator)
    }
    val topNodes = matchedActions + matchedRepos
    val localAndRemoteNodes = getLocalAndRemoteTopLevelNodes(localBranchesTree, remoteBranchesTree, tagsTree, recentCheckoutBranchesTree)

    return if (localAndRemoteNodes.isEmpty()) topNodes else topNodes + branchesSubtreeSeparator + localAndRemoteNodes
  }

  override fun getPreferredSelection(): TreePath? {
    return (actionsTree.topMatch ?: repositoriesTree.topMatch ?: getPreferredBranch())
      ?.let { createTreePathFor(this, it) }
  }

  override fun notHaveFilteredNodes(): Boolean = super.notHaveFilteredNodes() && repositoriesTree.isEmpty()
}
