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
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.popup.GitBranchesTreePopupBase
import git4idea.ui.branch.popup.GitBranchesTreePopupFilterByRepository
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

class GitBranchesTreeMultiRepoFilteringModel(
  private val project: Project,
  private val repositories: List<GitRepository>,
  private val topLevelActions: List<Any> = emptyList()
) : GitBranchesTreeModel() {

  private val branchManager = project.service<GitBranchManager>()

  private val actionsSeparator = GitBranchesTreePopupBase.createTreeSeparator()
  private val repositoriesSeparator = GitBranchesTreePopupBase.createTreeSeparator()

  private lateinit var actionsTree: LazyActionsHolder
  private lateinit var repositoriesTree: LazyTopLevelRepositoryHolder
  private lateinit var commonLocalBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch>
  private lateinit var commonRemoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch>
  private lateinit var commonTagsTree: LazyRefsSubtreeHolder<GitTag>
  private lateinit var repositoriesWithBranchesTree: LazyRepositoryBranchesHolder

  override var nameMatcher: MinusculeMatcher? by observable(null) { _, _, matcher -> rebuild(matcher) }

  override var isPrefixGrouping: Boolean by equalVetoingObservable(branchManager.isGroupingEnabled(GROUPING_BY_DIRECTORY)) {
    nameMatcher = null // rebuild tree
  }

  fun init() {
    // set trees
    nameMatcher = null
  }

  private fun rebuild(matcher: MinusculeMatcher?) {
    branchesTreeCache.keys.clear()
    val localBranches = GitBranchUtil.getCommonLocalBranches(repositories)
    val remoteBranches = GitBranchUtil.getCommonRemoteBranches(repositories)
    val localFavorites = project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.LOCAL)
    val remoteFavorites = project.service<GitBranchManager>().getFavoriteBranches(GitBranchType.REMOTE)
    actionsTree = LazyActionsHolder(project, topLevelActions, matcher)
    repositoriesTree = LazyTopLevelRepositoryHolder(repositories, matcher)
    commonLocalBranchesTree = LazyRefsSubtreeHolder(repositories, localBranches, localFavorites, matcher, ::isPrefixGrouping)
    commonRemoteBranchesTree = LazyRefsSubtreeHolder(repositories, remoteBranches, remoteFavorites, matcher, ::isPrefixGrouping)
    repositoriesWithBranchesTree = LazyRepositoryBranchesHolder()
    initTags(matcher)
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun initTags(matcher: MinusculeMatcher?) {
    val tags = GitBranchUtil.getCommonTags(repositories)
    val favoriteTags = project.service<GitBranchManager>().getFavoriteBranches(GitTagType)
    commonTagsTree = LazyRefsSubtreeHolder(repositories, tags, favoriteTags, matcher, ::isPrefixGrouping)
  }

  override fun isLeaf(node: Any?): Boolean = node is GitReference || node is RefUnderRepository
                                             || (node === GitBranchType.LOCAL && commonLocalBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && commonRemoteBranchesTree.isEmpty())
                                             || (node is RefTypeUnderRepository && node.isEmpty())
                                             || (node is GitTagType && commonTagsTree.isEmpty())

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
      is GitRepository -> {
        branchesTreeCache.getOrPut(parent) {
          mutableListOf<RefTypeUnderRepository>().apply {
            if (!repositoriesWithBranchesTree.isLocalBranchesEmpty(parent)) {
              add(RefTypeUnderRepository(parent, GitBranchType.LOCAL))
            }
            if (!repositoriesWithBranchesTree.isRemoteBranchesEmpty(parent)) {
              add(RefTypeUnderRepository(parent, GitBranchType.REMOTE))
            }
            if (!repositoriesWithBranchesTree.isTagsEmpty(parent)) {
              add(RefTypeUnderRepository(parent, GitTagType))
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
    val localAndRemoteNodes = getLocalAndRemoteTopLevelNodes(commonLocalBranchesTree, commonRemoteBranchesTree, commonTagsTree)
    val notEmptyRepositories = repositoriesWithBranchesTree.getNotEmptyRepositories()
    if (localAndRemoteNodes.isNotEmpty() || notEmptyRepositories.isNotEmpty()) {
      addSeparatorIfNeeded(topNodes, repositoriesSeparator)
    }

    return topNodes + localAndRemoteNodes + notEmptyRepositories
  }

  private fun getTreeNodes(branchType: GitRefType, path: List<String>, repository: GitRepository? = null): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType && repository == null -> commonLocalBranchesTree.tree
      GitBranchType.LOCAL == branchType && repository != null -> repositoriesWithBranchesTree[repository].localBranches.tree
      GitBranchType.REMOTE == branchType && repository == null -> commonRemoteBranchesTree.tree
      GitBranchType.REMOTE == branchType && repository != null -> repositoriesWithBranchesTree[repository].remoteBranches.tree
      GitTagType == branchType && repository == null -> commonTagsTree.tree
      GitTagType == branchType && repository != null -> repositoriesWithBranchesTree[repository].tags.tree
      else -> emptyMap()
    }

    return buildBranchTreeNodes(branchType, branchesMap, path, repository)
  }

  override fun getPreferredSelection(): TreePath? =
    (actionsTree.topMatch ?: repositoriesTree.topMatch ?: getPreferredBranch())?.let { createTreePathFor(this, it) }

  private fun getPreferredBranch(): Any? =
    getPreferredBranch(project, repositories, nameMatcher, commonLocalBranchesTree, commonRemoteBranchesTree, commonTagsTree)
    ?: getPreferredRefUnderFirstNonEmptyRepo()

  private fun getPreferredRefUnderFirstNonEmptyRepo(): RefUnderRepository? {
    val nonEmptyRepo = repositories.firstOrNull(repositoriesWithBranchesTree::isNotEmpty) ?: return null

    return repositoriesWithBranchesTree[nonEmptyRepo]
      .let { getPreferredBranch(project, listOf(nonEmptyRepo), nameMatcher, it.localBranches, it.remoteBranches, it.tags) }
      ?.let { RefUnderRepository(nonEmptyRepo, it) }
  }

  override fun filterBranches(matcher: MinusculeMatcher?) {
    nameMatcher = matcher
  }

  private fun haveFilteredBranches(): Boolean =
    !actionsTree.isEmpty() || !repositoriesTree.isEmpty()
    || !commonLocalBranchesTree.isEmpty() || !commonRemoteBranchesTree.isEmpty()
    || !repositoriesWithBranchesTree.isLocalBranchesEmpty() || !repositoriesWithBranchesTree.isRemoteBranchesEmpty()
    || !commonTagsTree.isEmpty()

  private inner class LazyTopLevelRepositoryHolder(repositories: List<GitRepository>, matcher: MinusculeMatcher?) :
    LazyHolder<TopLevelRepository>(repositories.map(::TopLevelRepository), matcher,
                                   nodeNameSupplier = { it.presentableText },
                                   needFilter = { GitBranchesTreePopupFilterByRepository.isSelected(project) })

  private inner class LazyRepositoryBranchesHolder {

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
    fun getNotEmptyRepositories(): List<GitRepository> = repositories.filter(::isNotEmpty)
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
