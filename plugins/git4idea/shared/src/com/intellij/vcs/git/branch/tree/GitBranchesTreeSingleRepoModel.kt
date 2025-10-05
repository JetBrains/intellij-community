// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.vcs.git.branch.popup.GitBranchesPopupBase
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitStandardLocalBranch
import git4idea.GitTag
import git4idea.branch.GitRefType
import git4idea.config.GitVcsSettings
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

@ApiStatus.Internal
open class GitBranchesTreeSingleRepoModel(
  project: Project,
  val repository: GitRepositoryModel,
  topLevelActions: List<Any>,
) : GitBranchesTreeModel(project, topLevelActions, listOf(repository)) {
  private val actionsSeparator = GitBranchesPopupBase.createTreeSeparator()

  override fun rebuild(matcher: MinusculeMatcher?) {
    super.rebuild(matcher)
    val recentCheckoutBranches = getRecentBranches()
    recentCheckoutBranchesTree = LazyRefsSubtreeHolder(recentCheckoutBranches, matcher,
                                                       ::isPrefixGrouping,
                                                       refComparatorGetter = ::emptyBranchComparator)
  }

  override fun getLocalBranches(): Collection<GitStandardLocalBranch> = repository.state.localBranchesOrCurrent

  override fun getRecentBranches(): Collection<GitStandardLocalBranch> =
    if (GitVcsSettings.getInstance(project).showRecentBranches()) repository.state.recentBranches else emptyList()

  override fun getRemoteBranches(): Collection<GitRemoteBranch> = repository.state.remoteBranches

  override fun getTags(): Set<GitTag> = repository.state.tags

  override fun getChildren(parent: Any?): List<Any> {
    if (parent == null || notHaveFilteredNodes()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitRefType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) {
            getBranchTreeNodes(parent.type, parent.prefix).sortedWith(getSubTreeComparator())
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

  override fun getPreferredSelection(): TreePath? =
    (actionsTree.topMatch ?: getPreferredBranch())?.let { createTreePathFor(this, it) }

  protected fun getPreferredBranch(): GitReference? =
    getPreferredBranch(project, repositories, nameMatcher, localBranchesTree, remoteBranchesTree, tagsTree, recentCheckoutBranchesTree)

  protected open fun notHaveFilteredNodes(): Boolean {
    return nameMatcher != null
           && actionsTree.isEmpty()
           && areRefTreesEmpty()
  }
}
