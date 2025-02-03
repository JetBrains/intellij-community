// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

open class GitBranchesTreeSingleRepoModel(
  protected val project: Project,
  protected val repository: GitRepository,
  private val topLevelActions: List<Any> = emptyList(),
) : GitBranchesTreeModel() {

  private val actionsSeparator = GitBranchesTreePopupBase.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()

  internal lateinit var actionsTree: LazyActionsHolder
  internal lateinit var localBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch>
  internal lateinit var remoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch>
  internal lateinit var tagsTree: LazyRefsSubtreeHolder<GitTag>
  internal lateinit var recentCheckoutBranchesTree: LazyRefsSubtreeHolder<GitReference>

  override var nameMatcher: MinusculeMatcher? by observable(null) { _, _, matcher -> rebuild(matcher) }

  override var isPrefixGrouping: Boolean by equalVetoingObservable(branchManager.isGroupingEnabled(GROUPING_BY_DIRECTORY)) {
    nameMatcher = null // rebuild tree
  }

  fun init() {
    // set trees
    nameMatcher = null
  }

  protected open fun rebuild(matcher: MinusculeMatcher?) {
    branchesTreeCache.keys.clear()
    val localBranches = getLocalBranches()
    val remoteBranches = getRemoteBranches()
    val recentCheckoutBranches = getRecentBranches()

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

  protected open fun getLocalBranches(): Collection<GitLocalBranch> = repository.localBranchesOrCurrent

  protected open fun getRecentBranches(): Collection<GitLocalBranch> = repository.recentCheckoutBranches

  protected open fun getRemoteBranches(): Collection<GitRemoteBranch> = repository.branches.remoteBranches

  override fun isLeaf(node: Any?): Boolean = node is GitReference
                                             || node is RefUnderRepository
                                             || (node is GitRefType && getCorrespondingTree(node).isEmpty())

  override fun initTags(matcher: MinusculeMatcher?) {
    val tags = repository.tags
    val favoriteTags = project.service<GitBranchManager>().getFavoriteBranches(GitTagType)
    tagsTree = LazyRefsSubtreeHolder(listOf(repository), tags.keys, favoriteTags, matcher, ::isPrefixGrouping)
  }

  override fun getChildren(parent: Any?): List<Any> {
    if (parent == null || notHaveFilteredNodes()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitRefType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
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

  private fun getBranchTreeNodes(branchType: GitRefType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = getCorrespondingTree(branchType)
    return buildBranchTreeNodes(branchType, branchesMap, path)
  }

  private fun getCorrespondingTree(branchType: GitRefType): Map<String, Any> = when (branchType) {
    GitBranchType.REMOTE -> remoteBranchesTree.tree
    GitBranchType.RECENT -> recentCheckoutBranchesTree.tree
    GitBranchType.LOCAL -> localBranchesTree.tree
    GitTagType -> tagsTree.tree
  }

  override fun getPreferredSelection(): TreePath? =
    (actionsTree.topMatch ?: getPreferredBranch())?.let { createTreePathFor(this, it) }

  protected fun getPreferredBranch(): GitReference? =
    getPreferredBranch(project, listOf(repository), nameMatcher, localBranchesTree, remoteBranchesTree, tagsTree, recentCheckoutBranchesTree)

  override fun filterBranches(matcher: MinusculeMatcher?) {
    nameMatcher = matcher
  }

  protected open fun notHaveFilteredNodes(): Boolean {
    return nameMatcher != null
           && actionsTree.isEmpty()
           && recentCheckoutBranchesTree.isEmpty() && localBranchesTree.isEmpty() && remoteBranchesTree.isEmpty() && tagsTree.isEmpty()
  }
}
