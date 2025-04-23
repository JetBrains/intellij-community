// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import javax.swing.tree.TreePath

internal class GitBranchesTreeMultiRepoFilteringModel(
  project: Project,
  repositories: List<GitRepository>,
  topLevelActions: List<Any> = emptyList(),
) : GitBranchesTreeModel(project, topLevelActions, repositories) {
  private val actionsSeparator = GitBranchesTreePopupBase.createTreeSeparator()
  private val repositoriesSeparator = GitBranchesTreePopupBase.createTreeSeparator()

  private lateinit var repositoriesTree: LazyRepositoryHolder
  private lateinit var repositoriesWithBranchesTree: LazyRepositoryBranchesHolder

  override fun rebuild(matcher: MinusculeMatcher?) {
    super.rebuild(matcher)
    repositoriesTree = LazyRepositoryHolder(project, repositories, matcher, canHaveChildren = false)
    repositoriesWithBranchesTree = LazyRepositoryBranchesHolder()
  }

  override fun getLocalBranches() = GitBranchUtil.getCommonLocalBranches(repositories)

  override fun getRemoteBranches() = GitBranchUtil.getCommonRemoteBranches(repositories)

  override fun getTags() = GitBranchUtil.getCommonTags(repositories)

  override fun isLeaf(node: Any?): Boolean = super.isLeaf(node) || (node is RefTypeUnderRepository && node.isEmpty())

  private fun RefTypeUnderRepository.isEmpty() =
    type === GitBranchType.LOCAL && repositoriesWithBranchesTree.isLocalBranchesEmpty(repository)
    || type === GitBranchType.REMOTE && repositoriesWithBranchesTree.isRemoteBranchesEmpty(repository)

  override fun getChildren(parent: Any?): List<Any> {
    if (parent == null || !haveFilteredBranches()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) {
            getTreeNodes(parent.type, parent.prefix, parent.repository)
              .sortedWith(getSubTreeComparator(project.service<GitBranchManager>().getFavoriteBranches(parent.type), repositories))
          }
      }
      is RefTypeUnderRepository -> {
        branchesTreeCache.getOrPut(parent) { getTreeNodes(parent.type, emptyList(), parent.repository) }
      }
      is RepositoryNode -> {
        if (parent.isLeaf) emptyList() else branchesTreeCache.getOrPut(parent) {
          val repository = parent.repository
          buildList<RefTypeUnderRepository> {
            if (!repositoriesWithBranchesTree.isLocalBranchesEmpty(repository)) {
              add(RefTypeUnderRepository(repository, GitBranchType.LOCAL))
            }
            if (!repositoriesWithBranchesTree.isRemoteBranchesEmpty(repository)) {
              add(RefTypeUnderRepository(repository, GitBranchType.REMOTE))
            }
            if (!repositoriesWithBranchesTree.isTagsEmpty(repository)) {
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

  private fun getTreeNodes(branchType: GitRefType, path: List<String>, repository: GitRepository? = null): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType && repository == null -> localBranchesTree.tree
      GitBranchType.LOCAL == branchType && repository != null -> repositoriesWithBranchesTree[repository].localBranches.tree
      GitBranchType.REMOTE == branchType && repository == null -> remoteBranchesTree.tree
      GitBranchType.REMOTE == branchType && repository != null -> repositoriesWithBranchesTree[repository].remoteBranches.tree
      GitTagType == branchType && repository == null -> tagsTree.tree
      GitTagType == branchType && repository != null -> repositoriesWithBranchesTree[repository].tags.tree
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
    val nonEmptyRepo = repositories.firstOrNull(repositoriesWithBranchesTree::isNotEmpty) ?: return null

    return repositoriesWithBranchesTree[nonEmptyRepo]
      .let { getPreferredBranch(project, listOf(nonEmptyRepo), nameMatcher, it.localBranches, it.remoteBranches, it.tags) }
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

    private val tree by lazy {
      if (repositories.size > 1) hashMapOf(*repositories.map { it to LazyRepositoryBranchesSubtreeHolder(it) }.toTypedArray())
      else hashMapOf()
    }

    operator fun get(repository: GitRepository) = tree.getOrPut(repository) { LazyRepositoryBranchesSubtreeHolder(repository) }

    fun isLocalBranchesEmpty() = tree.values.all { it.localBranches.isEmpty() }
    fun isLocalBranchesEmpty(repository: GitRepository) = tree[repository]?.localBranches?.isEmpty() ?: true
    fun isRemoteBranchesEmpty() = tree.values.all { it.remoteBranches.isEmpty() }
    fun isRemoteBranchesEmpty(repository: GitRepository) = tree[repository]?.remoteBranches?.isEmpty() ?: true
    fun isTagsEmpty() = tree.values.all { it.tags.isEmpty() }
    fun isTagsEmpty(repository: GitRepository) = tree[repository]?.tags?.isEmpty() ?: true
    fun isNotEmpty(repository: GitRepository) = !isLocalBranchesEmpty(repository) || !isRemoteBranchesEmpty(repository) || !isTagsEmpty(repository)

    fun getRepoNodes(): List<RepositoryNode> = repositoryNodes.filter { isNotEmpty(it.repository) }
  }

  private inner class LazyRepositoryBranchesSubtreeHolder(repository: GitRepository) {
    val localBranches by lazy {
      LazyRefsSubtreeHolder(listOf(repository),
                            repository.localBranchesOrCurrent,
                            project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.LOCAL), nameMatcher,
                            ::isPrefixGrouping)
    }
    val remoteBranches by lazy {
      LazyRefsSubtreeHolder(listOf(repository),
                            repository.branches.remoteBranches,
                            project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.REMOTE), nameMatcher,
                            ::isPrefixGrouping)
    }

    val tags by lazy {
      LazyRefsSubtreeHolder(listOf(repository),
                            repository.tagHolder.getTags().keys,
                            project.service<GitBranchManager>().getFavoriteBranches(GitTagType), nameMatcher,
                            ::isPrefixGrouping)
    }
  }
}
