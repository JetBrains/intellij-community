// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.vcs.git.branch.popup.GitBranchesPopupBase
import com.intellij.vcs.git.ref.GitRefUtil
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitReference
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import javax.swing.tree.TreePath

internal class GitBranchesTreeMultiRepoModel(
  project: Project,
  repositories: List<GitRepositoryModel>,
  topLevelActions: List<Any>
) : GitBranchesTreeModel(project, topLevelActions, repositories) {
  private val repositoriesNodes = this.repositories.map { RepositoryNode(it, isLeaf = true) }

  private val branchesSubtreeSeparator = GitBranchesPopupBase.createTreeSeparator()

  override fun getLocalBranches() = GitRefUtil.getCommonLocalBranches(repositories)

  override fun getRemoteBranches() = GitRefUtil.getCommonRemoteBranches(repositories)

  override fun getTags() = GitRefUtil.getCommonTags(repositories)

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
    getPreferredBranch(project, repositories, null, localBranchesTree, remoteBranchesTree, tagsTree)
}
