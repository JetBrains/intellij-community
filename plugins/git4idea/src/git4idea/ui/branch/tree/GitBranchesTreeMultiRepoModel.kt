// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitTag
import git4idea.branch.*
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import javax.swing.tree.TreePath

class GitBranchesTreeMultiRepoModel(
  private val project: Project,
  private val repositories: List<GitRepository>,
  private val topLevelActions: List<Any> = emptyList()
) : GitBranchesTreeModel() {

  private val branchesSubtreeSeparator = GitBranchesTreePopupBase.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()

  private lateinit var commonLocalBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch>
  private lateinit var commonRemoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch>
  private lateinit var commonTagsTree: LazyRefsSubtreeHolder<GitTag>

  override var isPrefixGrouping: Boolean by equalVetoingObservable(branchManager.isGroupingEnabled(GROUPING_BY_DIRECTORY)) {
    rebuild()
  }

  init {
    // set trees
    rebuild()
  }

  private fun rebuild() {
    branchesTreeCache.keys.clear()
    val localBranches = GitBranchUtil.getCommonLocalBranches(repositories)
    val remoteBranches = GitBranchUtil.getCommonRemoteBranches(repositories)
    val localFavorites = project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.LOCAL)
    val remoteFavorites = project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.REMOTE)
    commonLocalBranchesTree = LazyRefsSubtreeHolder(repositories, localBranches, localFavorites, null, ::isPrefixGrouping)
    commonRemoteBranchesTree = LazyRefsSubtreeHolder(repositories, remoteBranches, remoteFavorites, null, ::isPrefixGrouping)
    initTags(null)
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun initTags(matcher: MinusculeMatcher?) {
    val tags = GitBranchUtil.getCommonTags(repositories)
    val favoriteTags = project.service<GitBranchManager>().getFavoriteBranches(GitTagType)
    commonTagsTree = LazyRefsSubtreeHolder(repositories, tags, favoriteTags, null, ::isPrefixGrouping)
  }

  override fun isLeaf(node: Any?): Boolean = node is GitReference || node is RefUnderRepository
                                             || (node === GitBranchType.LOCAL && commonLocalBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && commonRemoteBranchesTree.isEmpty())
                                             || (node === GitTagType && commonTagsTree.isEmpty())

  override fun getChildren(parent: Any?): List<Any> {
    if (parent == null) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitRefType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) {
            getBranchTreeNodes(parent.type, parent.prefix)
              .sortedWith(getSubTreeComparator(project.service<GitBranchManager>().getFavoriteBranches(parent.type), repositories))
          }
      }
      else -> emptyList()
    }
  }

  private fun getTopLevelNodes(): List<Any> {
    val topNodes = topLevelActions + repositories
    val localAndRemoteNodes = getLocalAndRemoteTopLevelNodes(commonLocalBranchesTree, commonRemoteBranchesTree, commonTagsTree)

    return if (localAndRemoteNodes.isEmpty()) topNodes else topNodes + branchesSubtreeSeparator + localAndRemoteNodes
  }

  private fun getBranchTreeNodes(branchType: GitRefType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType -> commonLocalBranchesTree.tree
      GitBranchType.REMOTE == branchType -> commonRemoteBranchesTree.tree
      branchType == GitTagType -> commonTagsTree.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path)
  }

  override fun getPreferredSelection(): TreePath? = getPreferredBranch()?.let { createTreePathFor(this, it) }

  private fun getPreferredBranch(): GitReference? =
    getPreferredBranch(project, repositories, null, commonLocalBranchesTree, commonRemoteBranchesTree, commonTagsTree)
}
