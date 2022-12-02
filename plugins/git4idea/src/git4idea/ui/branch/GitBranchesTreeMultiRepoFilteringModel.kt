// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitBranch
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchesTreeModel.*
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

class GitBranchesTreeMultiRepoFilteringModel(
  private val project: Project,
  private val repositories: List<GitRepository>,
  private val topLevelActions: List<Any> = emptyList()
) : AbstractTreeModel(), GitBranchesTreeModel {

  private val branchesSubtreeSeparator = GitBranchesTreePopup.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()

  private lateinit var commonLocalBranchesTree: LazyBranchesSubtreeHolder
  private lateinit var commonRemoteBranchesTree: LazyBranchesSubtreeHolder
  private lateinit var repositoriesTree: LazyRepositoryBranchesHolder

  private val branchesTreeCache = mutableMapOf<Any, List<Any>>()

  private var branchTypeFilter: GitBranchType? = null
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
    val localBranches = GitBranchUtil.getCommonLocalBranches(repositories)
    val remoteBranches = GitBranchUtil.getCommonRemoteBranches(repositories)
    commonLocalBranchesTree = LazyBranchesSubtreeHolder(localBranches, repositories, matcher, ::isPrefixGrouping)
    commonRemoteBranchesTree = LazyBranchesSubtreeHolder(remoteBranches, repositories, matcher, ::isPrefixGrouping)
    repositoriesTree = LazyRepositoryBranchesHolder()
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitBranch || node is BranchUnderRepository
                                             || (node === GitBranchType.LOCAL && commonLocalBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && commonRemoteBranchesTree.isEmpty())
                                             || (node is BranchTypeUnderRepository && node.isEmpty())

  private fun BranchTypeUnderRepository.isEmpty() =
    type === GitBranchType.LOCAL && repositoriesTree.isLocalBranchesEmpty(repository)
    || type === GitBranchType.REMOTE && repositoriesTree.isRemoteBranchesEmpty(repository)

  private fun getChildren(parent: Any?): List<Any> {
    if (parent == null || !haveFilteredBranches()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) {
            getBranchTreeNodes(parent.type, parent.prefix, parent.repository).sortedWith(getSubTreeComparator(repositories))
          }
      }
      is BranchTypeUnderRepository -> {
        branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent.type, emptyList(), parent.repository) }
      }
      is GitRepository -> {
        branchesTreeCache.getOrPut(parent) {
          val typeFilter = branchTypeFilter
          if (typeFilter != null) {
            listOf(BranchTypeUnderRepository(parent, typeFilter))
          }
          else {
            listOf(BranchTypeUnderRepository(parent, GitBranchType.LOCAL),
                   BranchTypeUnderRepository(parent, GitBranchType.REMOTE))
          }
        }

      }
      else -> emptyList()
    }
  }

  private fun getTopLevelNodes(): List<Any> {
    val repositoriesExpanded = listOf(branchesSubtreeSeparator) + repositories

    return if (branchTypeFilter != null) topLevelActions + branchTypeFilter!! + repositoriesExpanded
    else topLevelActions + GitBranchType.LOCAL + GitBranchType.REMOTE + repositoriesExpanded
  }

  private fun getBranchTreeNodes(branchType: GitBranchType, path: List<String>, repository: GitRepository? = null): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType && repository == null -> commonLocalBranchesTree.tree
      GitBranchType.LOCAL == branchType && repository != null -> repositoriesTree[repository].localBranches.tree
      GitBranchType.REMOTE == branchType && repository == null -> commonRemoteBranchesTree.tree
      GitBranchType.REMOTE == branchType && repository != null -> repositoriesTree[repository].remoteBranches.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path, repository)
  }

  override fun getPreferredSelection(): TreePath? = getPreferredBranch()?.let { createTreePathFor(this, it) }

  private fun getPreferredBranch(): GitBranch? =
    getPreferredBranch(project, repositories, branchNameMatcher, branchTypeFilter, commonLocalBranchesTree, commonRemoteBranchesTree)

  override fun filterBranches(type: GitBranchType?, matcher: MinusculeMatcher?) {
    branchTypeFilter = type
    branchNameMatcher = matcher
  }

  private fun haveFilteredBranches(): Boolean =
    !commonLocalBranchesTree.isEmpty() || !commonRemoteBranchesTree.isEmpty()
    || !repositoriesTree.isLocalBranchesEmpty() || !repositoriesTree.isRemoteBranchesEmpty()

  private inner class LazyRepositoryBranchesHolder {

    private val tree by lazy {
      if (repositories.size > 1) mutableMapOf(*repositories.map { it to LazyRepositoryBranchesSubtreeHolder(it) }.toTypedArray())
      else mutableMapOf()
    }

    operator fun get(repository: GitRepository) = tree.getOrPut(repository) { LazyRepositoryBranchesSubtreeHolder(repository) }

    fun isLocalBranchesEmpty() = tree.values.all { it.localBranches.isEmpty() }
    fun isLocalBranchesEmpty(repository: GitRepository) = tree[repository]?.localBranches?.isEmpty() ?: true
    fun isRemoteBranchesEmpty() = tree.values.all { it.remoteBranches.isEmpty() }
    fun isRemoteBranchesEmpty(repository: GitRepository) = tree[repository]?.remoteBranches?.isEmpty() ?: true
  }

  private inner class LazyRepositoryBranchesSubtreeHolder(repository: GitRepository) {
    val localBranches by lazy {
      LazyBranchesSubtreeHolder(repository.branches.localBranches, listOf(repository), branchNameMatcher, ::isPrefixGrouping)
    }
    val remoteBranches by lazy {
      LazyBranchesSubtreeHolder(repository.branches.remoteBranches, listOf(repository), branchNameMatcher, ::isPrefixGrouping)
    }
  }
}
