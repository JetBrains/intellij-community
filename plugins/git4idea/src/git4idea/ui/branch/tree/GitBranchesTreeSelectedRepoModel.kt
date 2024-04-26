// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopup
import javax.swing.tree.TreePath

class GitBranchesTreeSelectedRepoModel(
  project: Project,
  selectedRepository: GitRepository,
  private val repositories: List<GitRepository>,
  topLevelActions: List<Any> = emptyList()
) : GitBranchesTreeSingleRepoModel(project, selectedRepository, topLevelActions) {
  val selectedRepository get() = repository

  private val actionsSeparator = GitBranchesTreePopup.createTreeSeparator()
  private val branchesSubtreeSeparator = GitBranchesTreePopup.createTreeSeparator()

  private lateinit var repositoriesTree: LazyRepositoryHolder

  override fun rebuild(matcher: MinusculeMatcher?) {
    repositoriesTree = LazyRepositoryHolder(project, repositories, matcher)
    super.rebuild(matcher)
  }

  override fun getTopLevelNodes(): List<Any> {
    val matchedActions = actionsTree.match
    val matchedRepos = repositoriesTree.match
    if (matchedRepos.isNotEmpty()) {
      addSeparatorIfNeeded(matchedActions, actionsSeparator)
    }
    val topNodes = matchedActions + matchedRepos
    val localAndRemoteNodes = getLocalAndRemoteTopLevelNodes(localBranchesTree, remoteBranchesTree, recentCheckoutBranchesTree)

    return if (localAndRemoteNodes.isEmpty()) topNodes else topNodes + branchesSubtreeSeparator + localAndRemoteNodes
  }

  override fun getPreferredSelection(): TreePath? {
    return (actionsTree.topMatch ?: repositoriesTree.topMatch ?: getPreferredBranch())
      ?.let { createTreePathFor(this, it) }
  }

  override fun notHaveFilteredNodes(): Boolean = super.notHaveFilteredNodes() && repositoriesTree.isEmpty()
}
