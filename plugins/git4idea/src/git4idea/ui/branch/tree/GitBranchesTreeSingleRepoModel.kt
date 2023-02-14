// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitBranch
import git4idea.branch.GitBranchType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.tree.GitBranchesTreeModel.*
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

open class GitBranchesTreeSingleRepoModel(
  private val project: Project,
  protected val repository: GitRepository,
  protected val topLevelActions: List<Any> = emptyList()
) : AbstractTreeModel(), GitBranchesTreeModel {

  private val branchManager = project.service<GitBranchManager>()

  internal lateinit var localBranchesTree: LazyBranchesSubtreeHolder
  internal lateinit var remoteBranchesTree: LazyBranchesSubtreeHolder

  private val branchesTreeCache = mutableMapOf<Any, List<Any>>()

  private var branchNameMatcher: MinusculeMatcher? by observable(null) { _, _, matcher -> rebuild(matcher) }

  override var isPrefixGrouping: Boolean by equalVetoingObservable(branchManager.isGroupingEnabled(GROUPING_BY_DIRECTORY)) {
    branchNameMatcher = null // rebuild tree
  }

  init {
    // set trees
    branchNameMatcher = null
  }

  private fun rebuild(matcher: MinusculeMatcher?) {
    branchesTreeCache.keys.clear()
    val localBranches = repository.localBranchesOrCurrent
    val remoteBranches = repository.branches.remoteBranches
    localBranchesTree = LazyBranchesSubtreeHolder(localBranches, listOf(repository), matcher, ::isPrefixGrouping)
    remoteBranchesTree = LazyBranchesSubtreeHolder(remoteBranches, listOf(repository), matcher, ::isPrefixGrouping)
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitBranch || node is BranchUnderRepository
                                             || (node === GitBranchType.LOCAL && localBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && remoteBranchesTree.isEmpty())

  private fun getChildren(parent: Any?): List<Any> {
    if (parent == null || notHaveFilteredBranches()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) { getBranchTreeNodes(parent.type, parent.prefix).sortedWith(getSubTreeComparator(listOf(repository))) }
      }
      else -> emptyList()
    }
  }

  protected open fun getTopLevelNodes(): List<Any> {
    return topLevelActions + getLocalAndRemoteTopLevelNodes(localBranchesTree, remoteBranchesTree)
  }

  private fun getBranchTreeNodes(branchType: GitBranchType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType -> localBranchesTree.tree
      GitBranchType.REMOTE == branchType -> remoteBranchesTree.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path)
  }

  override fun getPreferredSelection(): TreePath? = getPreferredBranch()?.let { createTreePathFor(this, it) }

  private fun getPreferredBranch(): GitBranch? =
    getPreferredBranch(project, listOf(repository), branchNameMatcher, localBranchesTree, remoteBranchesTree)

  override fun filterBranches(matcher: MinusculeMatcher?) {
    branchNameMatcher = matcher
  }

  private fun notHaveFilteredBranches(): Boolean =
    branchNameMatcher != null && localBranchesTree.isEmpty() && remoteBranchesTree.isEmpty()
}
