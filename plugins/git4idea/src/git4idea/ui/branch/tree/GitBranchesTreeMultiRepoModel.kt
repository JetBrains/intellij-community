// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.openapi.project.Project
import git4idea.GitReference
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import javax.swing.tree.TreePath

internal class GitBranchesTreeMultiRepoModel(
  project: Project,
  repositories: List<GitRepository>,
  topLevelActions: List<Any>
) : GitBranchesTreeModel(project, topLevelActions, repositories) {
  private val repositoriesNodes = repositoriesFrontendModel.map { RepositoryNode(it, isLeaf = true) }

  private val branchesSubtreeSeparator = GitBranchesTreePopupBase.createTreeSeparator()

  override fun getLocalBranches() = GitBranchUtil.getCommonLocalBranches(repositories)

  override fun getRemoteBranches() = GitBranchUtil.getCommonRemoteBranches(repositories)

  override fun getTags() = GitBranchUtil.getCommonTags(repositories)

  override fun getChildren(parent: Any?): List<Any> {
    if (parent == null) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitRefType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) {
            getBranchTreeNodes(parent.type, parent.prefix)
              .sortedWith(getSubTreeComparator())
          }
      }
      else -> emptyList()
    }
  }

  private fun getTopLevelNodes(): List<Any> {
    val topNodes = actionsTree.match + repositoriesNodes
    val localAndRemoteNodes = getLocalAndRemoteTopLevelNodes(localBranchesTree, remoteBranchesTree, tagsTree)

    return if (localAndRemoteNodes.isEmpty()) topNodes else topNodes + branchesSubtreeSeparator + localAndRemoteNodes
  }

  private fun getBranchTreeNodes(branchType: GitRefType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType -> localBranchesTree.tree
      GitBranchType.REMOTE == branchType -> remoteBranchesTree.tree
      branchType == GitTagType -> tagsTree.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path)
  }

  override fun getPreferredSelection(): TreePath? = getPreferredBranch()?.let { createTreePathFor(this, it) }

  private fun getPreferredBranch(): GitReference? =
    getPreferredBranch(project, repositoriesFrontendModel, null, localBranchesTree, remoteBranchesTree, tagsTree)
}
