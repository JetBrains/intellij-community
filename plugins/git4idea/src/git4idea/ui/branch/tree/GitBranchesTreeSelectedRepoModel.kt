// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopup

class GitBranchesTreeSelectedRepoModel(
  project: Project,
  selectedRepository: GitRepository,
  private val repositories: List<GitRepository>,
  topLevelActions: List<Any> = emptyList()
) : GitBranchesTreeSingleRepoModel(project, selectedRepository, topLevelActions) {
  val selectedRepository get() = repository

  private val branchesSubtreeSeparator = GitBranchesTreePopup.createTreeSeparator()

  override fun getTopLevelNodes(): List<Any> {
    val topNodes = topLevelActions + repositories
    val localAndRemoteNodes = getLocalAndRemoteTopLevelNodes(localBranchesTree, remoteBranchesTree, recentCheckoutBranchesTree)

    return if (localAndRemoteNodes.isEmpty()) topNodes else topNodes + branchesSubtreeSeparator + localAndRemoteNodes
  }
}
