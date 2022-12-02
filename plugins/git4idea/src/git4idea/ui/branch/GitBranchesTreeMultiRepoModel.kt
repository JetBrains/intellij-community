// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitBranch
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchesTreeModel.*
import javax.swing.tree.TreePath

class GitBranchesTreeMultiRepoModel(
  private val project: Project,
  private val repositories: List<GitRepository>,
  private val topLevelActions: List<Any> = emptyList()
) : AbstractTreeModel(), GitBranchesTreeModel {

  private val branchesSubtreeSeparator = GitBranchesTreePopup.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()

  private lateinit var commonLocalBranchesTree: LazyBranchesSubtreeHolder
  private lateinit var commonRemoteBranchesTree: LazyBranchesSubtreeHolder

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
    commonLocalBranchesTree = LazyBranchesSubtreeHolder(localBranches, repositories, null, ::isPrefixGrouping)
    commonRemoteBranchesTree = LazyBranchesSubtreeHolder(remoteBranches, repositories, null, ::isPrefixGrouping)
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitBranch || node is BranchUnderRepository
                                             || (node === GitBranchType.LOCAL && commonLocalBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && commonRemoteBranchesTree.isEmpty())

  private fun getChildren(parent: Any?): List<Any> {
    if (parent == null || !haveFilteredBranches()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) { getBranchTreeNodes(parent.type, parent.prefix).sortedWith(getSubTreeComparator(repositories)) }
      }
      else -> emptyList()
    }
  }

  private fun getTopLevelNodes(): List<Any> {
    return topLevelActions + repositories + branchesSubtreeSeparator + GitBranchType.LOCAL + GitBranchType.REMOTE
  }

  private fun getBranchTreeNodes(branchType: GitBranchType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType -> commonLocalBranchesTree.tree
      GitBranchType.REMOTE == branchType -> commonRemoteBranchesTree.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path)
  }

  override fun getPreferredSelection(): TreePath? = getPreferredBranch()?.let { createTreePathFor(this, it) }

  private fun getPreferredBranch(): GitBranch? =
    getPreferredBranch(project, repositories, null, null, commonLocalBranchesTree, commonRemoteBranchesTree)

  private fun haveFilteredBranches(): Boolean = !commonLocalBranchesTree.isEmpty() || !commonRemoteBranchesTree.isEmpty()
}
