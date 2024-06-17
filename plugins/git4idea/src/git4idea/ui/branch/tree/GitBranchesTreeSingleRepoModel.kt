// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.branch.BranchType
import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitTag
import git4idea.branch.GitBranchType
import git4idea.branch.GitTagType
import git4idea.branch.TagsNode
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopup
import git4idea.ui.branch.tree.GitBranchesTreeModel.*
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

open class GitBranchesTreeSingleRepoModel(
  protected val project: Project,
  protected val repository: GitRepository,
  private val topLevelActions: List<Any> = emptyList()
) : AbstractTreeModel(), GitBranchesTreeModel {

  private val actionsSeparator = GitBranchesTreePopup.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()

  internal lateinit var actionsTree: LazyActionsHolder
  internal lateinit var localBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch>
  internal lateinit var remoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch>
  internal lateinit var tagsTree: LazyRefsSubtreeHolder<GitTag>
  internal lateinit var recentCheckoutBranchesTree: LazyRefsSubtreeHolder<GitReference>

  private val branchesTreeCache = mutableMapOf<Any, List<Any>>()

  private var nameMatcher: MinusculeMatcher? by observable(null) { _, _, matcher -> rebuild(matcher) }

  override var isPrefixGrouping: Boolean by equalVetoingObservable(branchManager.isGroupingEnabled(GROUPING_BY_DIRECTORY)) {
    nameMatcher = null // rebuild tree
  }

  fun init() {
    // set trees
    nameMatcher = null
  }

  protected open fun rebuild(matcher: MinusculeMatcher?) {
    branchesTreeCache.keys.clear()
    val localBranches = repository.localBranchesOrCurrent
    val remoteBranches = repository.branches.remoteBranches
    val recentCheckoutBranches = repository.recentCheckoutBranches

    val localFavorites = project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.LOCAL)
    val remoteFavorites = project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.REMOTE)
    actionsTree = LazyActionsHolder(project, topLevelActions, matcher)
    localBranchesTree = LazyRefsSubtreeHolder(listOf(repository), localBranches, localFavorites, matcher, ::isPrefixGrouping,
                                              recentCheckoutBranches::contains)
    remoteBranchesTree = LazyRefsSubtreeHolder(listOf(repository), remoteBranches, remoteFavorites, matcher, ::isPrefixGrouping)
    recentCheckoutBranchesTree = LazyRefsSubtreeHolder(listOf(repository), recentCheckoutBranches, localFavorites, matcher,
                                                       ::isPrefixGrouping,
                                                       refComparatorGetter = ::emptyBranchComparator)
    initTags(matcher)
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitReference || node is RefUnderRepository
                                             || (node === RecentNode && recentCheckoutBranchesTree.isEmpty())
                                             || (node === GitBranchType.LOCAL && localBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && remoteBranchesTree.isEmpty())
                                             || (node === TagsNode && tagsTree.isEmpty())

  private fun initTags(matcher: MinusculeMatcher?) {
    val tags = repository.tags
    val favoriteTags = project.service<GitBranchManager>().getFavoriteBranches(GitTagType)
    tagsTree = LazyRefsSubtreeHolder(listOf(repository), tags.keys, favoriteTags, matcher, ::isPrefixGrouping)
  }

  private fun getChildren(parent: Any?): List<Any> {
    if (parent == null || notHaveFilteredNodes()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is BranchType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) {
            getBranchTreeNodes(parent.type, parent.prefix).sortedWith(
              getSubTreeComparator(project.service<GitBranchManager>().getFavoriteBranches(parent.type), listOf(repository)))
          }
      }
      else -> emptyList()
    }
  }

  protected open fun getTopLevelNodes(): List<Any> {
    val matchedActions = actionsTree.match
    val localAndRemoteTopLevelNodes = getLocalAndRemoteTopLevelNodes(localBranchesTree, remoteBranchesTree, tagsTree, recentCheckoutBranchesTree)
    if (localAndRemoteTopLevelNodes.isNotEmpty()) {
      addSeparatorIfNeeded(matchedActions, actionsSeparator)
    }

    return matchedActions + localAndRemoteTopLevelNodes
  }

  private fun getBranchTreeNodes(branchType: BranchType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when {
      RecentNode == branchType -> recentCheckoutBranchesTree.tree
      TagsNode == branchType -> {
        tagsTree.tree
      }
      GitBranchType.LOCAL == branchType -> localBranchesTree.tree
      GitBranchType.REMOTE == branchType -> remoteBranchesTree.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path)
  }

  override fun getPreferredSelection(): TreePath? =
    (actionsTree.topMatch ?: getPreferredBranch())?.let { createTreePathFor(this, it) }

  override fun updateTags() {
    val indexOfTagsNode = getIndexOfChild(root, TagsNode)
    initTags(nameMatcher)
    branchesTreeCache.keys.clear()
    if (indexOfTagsNode < 0) {
      treeStructureChanged(TreePath(arrayOf(root)), null, null)
    }
    else {
      treeStructureChanged(TreePath(arrayOf(root)), intArrayOf(indexOfTagsNode), arrayOf(TagsNode))
    }
  }

  protected fun getPreferredBranch(): GitBranch? =
    getPreferredBranch(project, listOf(repository), nameMatcher, localBranchesTree, remoteBranchesTree, recentCheckoutBranchesTree)

  override fun filterBranches(matcher: MinusculeMatcher?) {
    nameMatcher = matcher
  }

  protected open fun notHaveFilteredNodes(): Boolean {
    return nameMatcher != null
           && actionsTree.isEmpty()
           && recentCheckoutBranchesTree.isEmpty() && localBranchesTree.isEmpty() && remoteBranchesTree.isEmpty() && tagsTree.isEmpty()
  }
}
