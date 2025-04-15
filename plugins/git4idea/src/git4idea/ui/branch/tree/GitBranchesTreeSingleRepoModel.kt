// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import javax.swing.tree.TreePath

internal open class GitBranchesTreeSingleRepoModel(
  project: Project,
  val repository: GitRepository,
  topLevelActions: List<Any>,
) : GitBranchesTreeModel(project, topLevelActions, listOf(repository)) {
  private val actionsSeparator = GitBranchesTreePopupBase.createTreeSeparator()

  protected lateinit var recentCheckoutBranchesTree: LazyRefsSubtreeHolder<GitReference>

  override fun rebuild(matcher: MinusculeMatcher?) {
    super.rebuild(matcher)

    val localFavorites = project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.LOCAL)
    val recentCheckoutBranches = getRecentBranches()
    recentCheckoutBranchesTree = LazyRefsSubtreeHolder(listOf(repository), recentCheckoutBranches, localFavorites, matcher,
                                                       ::isPrefixGrouping,
                                                       refComparatorGetter = ::emptyBranchComparator)
  }

  override fun getLocalBranches(): Collection<GitLocalBranch> = repository.localBranchesOrCurrent

  override fun getRecentBranches(): Collection<GitLocalBranch> = repository.recentCheckoutBranches

  override fun getRemoteBranches(): Collection<GitRemoteBranch> = repository.branches.remoteBranches

  override fun isLeaf(node: Any?): Boolean = node is GitReference
                                             || node is RefUnderRepository
                                             || (node is GitRefType && getCorrespondingTree(node).isEmpty())

  override fun getTags() = repository.tags.keys

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

  protected open fun notHaveFilteredNodes(): Boolean {
    return nameMatcher != null
           && actionsTree.isEmpty()
           && recentCheckoutBranchesTree.isEmpty() && localBranchesTree.isEmpty() && remoteBranchesTree.isEmpty() && tagsTree.isEmpty()
  }
}
