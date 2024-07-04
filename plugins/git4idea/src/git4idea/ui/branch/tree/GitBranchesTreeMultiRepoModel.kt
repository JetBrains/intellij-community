// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.branch.BranchType
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitTag
import git4idea.branch.*
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import git4idea.ui.branch.tree.GitBranchesTreeModel.*
import javax.swing.tree.TreePath

class GitBranchesTreeMultiRepoModel(
  private val project: Project,
  private val repositories: List<GitRepository>,
  private val topLevelActions: List<Any> = emptyList()
) : AbstractTreeModel(), GitBranchesTreeModel {

  private val branchesSubtreeSeparator = GitBranchesTreePopupBase.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()

  private lateinit var commonLocalBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch>
  private lateinit var commonRemoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch>
  private lateinit var commonTagsTree: LazyRefsSubtreeHolder<GitTag>

  private val branchesTreeCache = mutableMapOf<Any, List<Any>>()

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
    initTags()
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  private fun initTags() {
    val tags = GitBranchUtil.getCommonTags(repositories)
    val favoriteTags = project.service<GitBranchManager>().getFavoriteBranches(GitTagType)
    commonTagsTree = LazyRefsSubtreeHolder(repositories, tags, favoriteTags, null, ::isPrefixGrouping)
  }

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitReference || node is RefUnderRepository
                                             || (node === GitBranchType.LOCAL && commonLocalBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && commonRemoteBranchesTree.isEmpty())
                                             || (node === TagsNode && commonTagsTree.isEmpty())

  private fun getChildren(parent: Any?): List<Any> {
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

  private fun getBranchTreeNodes(branchType: BranchType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType -> commonLocalBranchesTree.tree
      GitBranchType.REMOTE == branchType -> commonRemoteBranchesTree.tree
      branchType == TagsNode -> commonTagsTree.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path)
  }

  override fun getPreferredSelection(): TreePath? = getPreferredBranch()?.let { createTreePathFor(this, it) }

  override fun updateTags() {
    val indexOfTagsNode = getIndexOfChild(root, TagsNode)
    initTags()
    branchesTreeCache.keys.clear()
    if (indexOfTagsNode < 0) {
      treeStructureChanged(TreePath(arrayOf(root)), null, null)
    }
    else {
      treeStructureChanged(TreePath(arrayOf(root)), intArrayOf(indexOfTagsNode), arrayOf(TagsNode))
    }
  }

  private fun getPreferredBranch(): GitReference? =
    getPreferredBranch(project, repositories, null, commonLocalBranchesTree, commonRemoteBranchesTree, commonTagsTree)
}
