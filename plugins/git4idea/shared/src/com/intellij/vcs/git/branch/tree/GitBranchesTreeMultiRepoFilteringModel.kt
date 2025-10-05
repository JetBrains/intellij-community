// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.vcs.git.branch.popup.GitBranchesPopupBase
import com.intellij.vcs.git.ref.GitRefUtil
import com.intellij.vcs.git.repo.GitRepositoryModel
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import javax.swing.tree.TreePath

internal class GitBranchesTreeMultiRepoFilteringModel(
  project: Project,
  repositories: List<GitRepositoryModel>,
  topLevelActions: List<Any> = emptyList(),
) : GitBranchesTreeModel(project, topLevelActions, repositories) {
  private val actionsSeparator = GitBranchesPopupBase.createTreeSeparator()
  private val repositoriesSeparator = GitBranchesPopupBase.createTreeSeparator()

  private lateinit var repositoriesTree: LazyRepositoryHolder
  private lateinit var repositoriesWithBranchesTree: LazyRepositoryBranchesHolder

  override fun rebuild(matcher: MinusculeMatcher?) {
    super.rebuild(matcher)
    repositoriesTree = LazyRepositoryHolder(project, repositories, matcher, canHaveChildren = false)
    repositoriesWithBranchesTree = LazyRepositoryBranchesHolder()
  }

  override fun getLocalBranches() = GitRefUtil.getCommonLocalBranches(repositories)

  override fun getRemoteBranches() = GitRefUtil.getCommonRemoteBranches(repositories)

  override fun getTags() = GitRefUtil.getCommonTags(repositories)

  override fun isLeaf(node: Any?): Boolean = super.isLeaf(node) || (node is RefTypeUnderRepository && node.isEmpty())

  private fun RefTypeUnderRepository.isEmpty() =
    type === GitBranchType.LOCAL && repositoriesWithBranchesTree.isLocalBranchesEmpty(repository.repositoryId)
    || type === GitBranchType.REMOTE && repositoriesWithBranchesTree.isRemoteBranchesEmpty(repository.repositoryId)

  override fun getChildren(parent: Any?): List<Any> {
    if (parent == null || !haveFilteredBranches()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) {
            getTreeNodes(parent.type, parent.prefix, parent.repository)
              .sortedWith(getSubTreeComparator())
          }
      }
      is RefTypeUnderRepository -> {
        branchesTreeCache.getOrPut(parent) { getTreeNodes(parent.type, emptyList(), parent.repository) }
      }
      is RepositoryNode -> {
        if (parent.isLeaf) emptyList() else branchesTreeCache.getOrPut(parent) {
          val repository = parent.repository
          buildList<RefTypeUnderRepository> {
            if (!repositoriesWithBranchesTree.isLocalBranchesEmpty(repository.repositoryId)) {
              add(RefTypeUnderRepository(repository, GitBranchType.LOCAL))
            }
            if (!repositoriesWithBranchesTree.isRemoteBranchesEmpty(repository.repositoryId)) {
              add(RefTypeUnderRepository(repository, GitBranchType.REMOTE))
            }
            if (!repositoriesWithBranchesTree.isTagsEmpty(repository.repositoryId)) {
              add(RefTypeUnderRepository(repository, GitTagType))
            }
          }
        }
      }
      else -> emptyList()
    }
  }

  private fun getTopLevelNodes(): List<Any> {
    val matchedActions = actionsTree.match
    val matchedRepos = repositoriesTree.match
    if (matchedRepos.isNotEmpty()) {
      addSeparatorIfNeeded(matchedActions, actionsSeparator)
    }
    val topNodes = matchedActions + matchedRepos
    val localAndRemoteNodes = getLocalAndRemoteTopLevelNodes(localBranchesTree, remoteBranchesTree, tagsTree)
    val notEmptyRepositories = repositoriesWithBranchesTree.getRepoNodes()
    if (localAndRemoteNodes.isNotEmpty() || notEmptyRepositories.isNotEmpty()) {
      addSeparatorIfNeeded(topNodes, repositoriesSeparator)
    }

    return topNodes + localAndRemoteNodes + notEmptyRepositories
  }

  private fun getTreeNodes(branchType: GitRefType, path: List<String>, repository: GitRepositoryModel? = null): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType && repository == null -> localBranchesTree.tree
      GitBranchType.LOCAL == branchType && repository != null -> repositoriesWithBranchesTree[repository.repositoryId].localBranches.tree
      GitBranchType.REMOTE == branchType && repository == null -> remoteBranchesTree.tree
      GitBranchType.REMOTE == branchType && repository != null -> repositoriesWithBranchesTree[repository.repositoryId].remoteBranches.tree
      GitTagType == branchType && repository == null -> tagsTree.tree
      GitTagType == branchType && repository != null -> repositoriesWithBranchesTree[repository.repositoryId].tags.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path, repository)
  }

  override fun getPreferredSelection(): TreePath? =
    (actionsTree.topMatch ?: repositoriesTree.topMatch ?: getPreferredBranch())?.let { createTreePathFor(this, it) }

  private fun getPreferredBranch(): Any? =
    getPreferredBranch(project, repositories, nameMatcher, localBranchesTree, remoteBranchesTree, tagsTree)
    ?: getPreferredRefUnderFirstNonEmptyRepo()

  private fun getPreferredRefUnderFirstNonEmptyRepo(): RefUnderRepository? {
    val nonEmptyRepo = repositories.firstOrNull { repositoriesWithBranchesTree.isNotEmpty(it.repositoryId) } ?: return null

    val subtreeHolder = repositoriesWithBranchesTree[nonEmptyRepo.repositoryId]
    return getPreferredBranch(project, listOf(nonEmptyRepo), nameMatcher, subtreeHolder.localBranches, subtreeHolder.remoteBranches, subtreeHolder.tags)
      ?.let { RefUnderRepository(nonEmptyRepo, it) }
  }

  private fun haveFilteredBranches(): Boolean =
    !actionsTree.isEmpty()
    || !repositoriesTree.isEmpty()
    || !areRefTreesEmpty()
    || !repositoriesWithBranchesTree.isLocalBranchesEmpty()
    || !repositoriesWithBranchesTree.isRemoteBranchesEmpty()

  private inner class LazyRepositoryBranchesHolder {
    private val repositoryNodes = repositories.map { RepositoryNode(it, isLeaf = false) }

    private val tree: Map<RepositoryId, LazyRepositoryBranchesSubtreeHolder> =
      repositories.associate { it.repositoryId to LazyRepositoryBranchesSubtreeHolder(it) }

    operator fun get(repositoryId: RepositoryId) = tree[repositoryId] ?: error("Repository $repositoryId is not present in the model")

    fun isLocalBranchesEmpty() = tree.values.all { it.localBranches.isEmpty() }
    fun isLocalBranchesEmpty(repositoryId: RepositoryId) = tree[repositoryId]?.localBranches?.isEmpty() ?: true
    fun isRemoteBranchesEmpty() = tree.values.all { it.remoteBranches.isEmpty() }
    fun isRemoteBranchesEmpty(repositoryId: RepositoryId) = tree[repositoryId]?.remoteBranches?.isEmpty() ?: true
    fun isTagsEmpty() = tree.values.all { it.tags.isEmpty() }
    fun isTagsEmpty(repositoryId: RepositoryId) = tree[repositoryId]?.tags?.isEmpty() ?: true
    fun isNotEmpty(repositoryId: RepositoryId) = !isLocalBranchesEmpty(repositoryId) || !isRemoteBranchesEmpty(repositoryId) || !isTagsEmpty(repositoryId)

    fun getRepoNodes(): List<RepositoryNode> = repositoryNodes.filter {
      isNotEmpty(it.repository.repositoryId)
    }
  }

  private inner class LazyRepositoryBranchesSubtreeHolder(private val repository: GitRepositoryModel) {
    val localBranches by lazy {
      LazyRefsSubtreeHolder(
        repository.state.localBranchesOrCurrent,
        nameMatcher,
        ::isPrefixGrouping,
        refComparatorGetter = { getRefComparator(listOf(repository)) })
    }
    val remoteBranches by lazy {
      LazyRefsSubtreeHolder(
        repository.state.remoteBranches,
        nameMatcher,
        ::isPrefixGrouping,
        refComparatorGetter = { getRefComparator(listOf(repository)) })
    }

    val tags by lazy {
      LazyRefsSubtreeHolder(
        repository.state.tags,
        nameMatcher,
        ::isPrefixGrouping,
        refComparatorGetter = { getRefComparator(listOf(repository)) })
    }
  }
}
