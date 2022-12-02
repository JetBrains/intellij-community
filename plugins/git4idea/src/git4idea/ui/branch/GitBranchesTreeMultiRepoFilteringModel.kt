// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.dvcs.branch.GroupingKey.GROUPING_BY_DIRECTORY
import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.containers.headTail
import com.intellij.util.containers.init
import com.intellij.util.ui.tree.AbstractTreeModel
import com.intellij.vcsUtil.Delegates.equalVetoingObservable
import git4idea.GitBranch
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchesTreeModel.BranchTypeUnderRepository
import git4idea.ui.branch.GitBranchesTreeModel.TreeRoot
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

private typealias PathAndBranch = Pair<List<String>, GitBranch>
private typealias MatchResult = Pair<Collection<GitBranch>, Pair<GitBranch, Int>?>

class GitBranchesTreeMultiRepoFilteringModel(
  private val project: Project,
  private val repositories: List<GitRepository>,
  private val topLevelActions: List<Any> = emptyList()
) : AbstractTreeModel(), GitBranchesTreeModel {

  private val branchesSubtreeSeparator = GitBranchesTreePopup.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()

  private val branchComparator = compareBy<GitBranch> {
    it.isNotCurrentBranch()
  } then compareBy {
    it.isNotFavorite()
  } then compareBy {
    !(isPrefixGrouping && it.name.contains('/'))
  } then compareBy { it.name }

  private val subTreeComparator = compareBy<Any> {
    it is GitBranch && it.isNotCurrentBranch() && it.isNotFavorite()
  } then compareBy {
    it is GitBranchesTreeModel.BranchesPrefixGroup
  }

  private fun GitBranch.isNotCurrentBranch() = !repositories.any { repo -> repo.currentBranch == this }
  private fun GitBranch.isNotFavorite() = !repositories.all { repo -> branchManager.isFavorite(GitBranchType.of(this), repo, name) }

  private lateinit var localBranchesTree: LazyBranchesSubtreeHolder
  private lateinit var remoteBranchesTree: LazyBranchesSubtreeHolder
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
    val localBranches = repositories.singleOrNull()?.branches?.localBranches ?: GitBranchUtil.getCommonLocalBranches(repositories)
    val remoteBranches = repositories.singleOrNull()?.branches?.remoteBranches ?: GitBranchUtil.getCommonRemoteBranches(repositories)
    localBranchesTree = LazyBranchesSubtreeHolder(localBranches, branchComparator, matcher)
    remoteBranchesTree = LazyBranchesSubtreeHolder(remoteBranches, branchComparator, matcher)
    repositoriesTree = LazyRepositoryBranchesHolder()
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitBranch || node is GitBranchesTreeModel.BranchUnderRepository
                                             || (node === GitBranchType.LOCAL && localBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && remoteBranchesTree.isEmpty())
                                             || (node is BranchTypeUnderRepository && node.isEmpty())

  private fun BranchTypeUnderRepository.isEmpty() = type === GitBranchType.LOCAL && repositoriesTree.isLocalBranchesEmpty(repository)
                                                    || type === GitBranchType.REMOTE && repositoriesTree.isRemoteBranchesEmpty(repository)

  private fun getChildren(parent: Any?): List<Any> {
    if (parent == null || !haveFilteredBranches()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is GitBranchesTreeModel.BranchesPrefixGroup -> {
        branchesTreeCache
          .getOrPut(parent) { getBranchTreeNodes(parent.type, parent.prefix, parent.repository).sortedWith(subTreeComparator) }
      }
      is BranchTypeUnderRepository -> {
        branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent.type, emptyList(), parent.repository) }
      }
      is GitRepository -> {
        when {
          isFilterActive() -> {
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
      else -> emptyList()
    }
  }

  private fun getTopLevelNodes(): List<Any> {
    val repositoriesCollapsed =
      if (repositories.size > 1 && !isFilterActive()) repositories + branchesSubtreeSeparator else emptyList()

    val repositoriesExpanded =
      if (repositories.size > 1 && isFilterActive()) listOf(branchesSubtreeSeparator) + repositories else emptyList()

    return if (branchTypeFilter != null) topLevelActions + repositoriesCollapsed + branchTypeFilter!! + repositoriesExpanded
    else topLevelActions + repositoriesCollapsed + GitBranchType.LOCAL + GitBranchType.REMOTE + repositoriesExpanded
  }

  private fun getBranchTreeNodes(branchType: GitBranchType, path: List<String>, repository: GitRepository? = null): List<Any> {
    val branchesMap: Map<String, Any> = when {
      GitBranchType.LOCAL == branchType && repository == null -> localBranchesTree.tree
      GitBranchType.LOCAL == branchType && repository != null -> repositoriesTree[repository].localBranches.tree
      GitBranchType.REMOTE == branchType && repository == null -> remoteBranchesTree.tree
      GitBranchType.REMOTE == branchType && repository != null -> repositoriesTree[repository].remoteBranches.tree
      else -> emptyMap()
    }

    if (path.isEmpty()) {
      return branchesMap.mapToNodes(branchType, path, repository)
    }
    else {
      var currentLevel = branchesMap
      for (prefixPart in path) {
        @Suppress("UNCHECKED_CAST")
        currentLevel = (currentLevel[prefixPart] as? Map<String, Any>) ?: return emptyList()
      }
      return currentLevel.mapToNodes(branchType, path, repository)
    }
  }

  private fun Map<String, Any>.mapToNodes(branchType: GitBranchType, path: List<String>, repository: GitRepository?): List<Any> {
    return entries.map { (name, value) ->
      if (value is GitBranch && repository != null) GitBranchesTreeModel.BranchUnderRepository(repository, value)
      else if (value is Map<*, *>) GitBranchesTreeModel.BranchesPrefixGroup(branchType, path + name, repository) else value
    }
  }

  override fun getPreferredSelection(): TreePath? = getPreferredBranch()?.let(::createTreePathFor)

  private fun getPreferredBranch(): GitBranch? {
    if (branchNameMatcher == null) {
      if (branchTypeFilter != GitBranchType.REMOTE) {
        if (repositories.size == 1) {
          val repository = repositories.single()
          val recentBranches = GitVcsSettings.getInstance(project).recentBranchesByRepository
          val recentBranch = recentBranches[repository.root.path]?.let { recentBranchName ->
            localBranchesTree.branches.find { it.name == recentBranchName }
          }
          if (recentBranch != null) {
            return recentBranch
          }

          val currentBranch = repository.currentBranch
          if (currentBranch != null) {
            return currentBranch
          }

          return null
        }
        else {
          val branch = (GitVcsSettings.getInstance(project).recentCommonBranch ?: repositories.getCommonCurrentBranch())
            ?.let { recentBranchName ->
              localBranchesTree.branches.find { it.name == recentBranchName }
            }
          return branch
        }
      }
      else {
        return null
      }
    }

    val localMatch = if (branchTypeFilter != GitBranchType.REMOTE) localBranchesTree.topMatch else null
    val remoteMatch = if (branchTypeFilter != GitBranchType.LOCAL) remoteBranchesTree.topMatch else null

    if (localMatch == null && remoteMatch == null) return null
    if (localMatch != null && remoteMatch == null) return localMatch.first
    if (localMatch == null && remoteMatch != null) return remoteMatch.first

    if (localMatch!!.second >= remoteMatch!!.second) {
      return localMatch.first
    }
    else {
      return remoteMatch.first
    }
  }

  override fun createTreePathFor(value: Any): TreePath? {
    val repository = value as? GitRepository
    if (repository != null) {
      return TreePathUtil.convertCollectionToTreePath(listOf(root, repository))
    }

    val typeUnderRepository = value as? BranchTypeUnderRepository
    if (typeUnderRepository != null) {
      return TreePathUtil.convertCollectionToTreePath(listOf(root, typeUnderRepository.repository, typeUnderRepository))
    }

    val branchUnderRepository = value as? GitBranchesTreeModel.BranchUnderRepository
    val branch = value as? GitBranch ?: branchUnderRepository?.branch ?: return null
    val branchType = GitBranchType.of(branch)
    val path = mutableListOf<Any>().apply {
      add(root)
      if (branchUnderRepository != null) {
        add(branchUnderRepository.repository)
        add(BranchTypeUnderRepository(branchUnderRepository.repository, branchType))
      }
      else {
        add(branchType)
      }
    }
    val nameParts = if (isPrefixGrouping) branch.name.split('/') else listOf(branch.name)
    val currentPrefix = mutableListOf<String>()
    for (prefixPart in nameParts.init()) {
      currentPrefix.add(prefixPart)
      path.add(GitBranchesTreeModel.BranchesPrefixGroup(branchType, currentPrefix.toList(), branchUnderRepository?.repository))
    }

    if (branchUnderRepository != null) {
      path.add(branchUnderRepository)
    }
    else {
      path.add(branch)
    }
    return TreePathUtil.convertCollectionToTreePath(path)
  }

  override fun filterBranches(type: GitBranchType?, matcher: MinusculeMatcher?) {
    branchTypeFilter = type
    branchNameMatcher = matcher
  }

  override fun isFilterActive() = (branchNameMatcher?.pattern?.let { it != "*" } ?: false)
                                  || branchTypeFilter != null

  private fun haveFilteredBranches(): Boolean =
    !localBranchesTree.isEmpty() || !remoteBranchesTree.isEmpty()
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
      LazyBranchesSubtreeHolder(repository.branches.localBranches, branchComparator, branchNameMatcher)
    }
    val remoteBranches by lazy {
      LazyBranchesSubtreeHolder(repository.branches.remoteBranches, branchComparator, branchNameMatcher)
    }
  }

  private inner class LazyBranchesSubtreeHolder(
    unsortedBranches: Collection<GitBranch>,
    comparator: Comparator<GitBranch>,
    private val matcher: MinusculeMatcher? = null
  ) {

    val branches by lazy { unsortedBranches.sortedWith(comparator) }

    fun isEmpty() = matchingResult.first.isEmpty()

    private val matchingResult: MatchResult by lazy {
      match(branches)
    }

    val tree: Map<String, Any> by lazy {
      val branchesList = matchingResult.first
      buildSubTree(branchesList.map { (if (isPrefixGrouping) it.name.split('/') else listOf(it.name)) to it })
    }

    val topMatch: Pair<GitBranch, Int>?
      get() = matchingResult.second

    private fun buildSubTree(prevLevel: List<PathAndBranch>): Map<String, Any> {
      val result = LinkedHashMap<String, Any>()
      val groups = LinkedHashMap<String, List<PathAndBranch>>()
      for ((pathParts, branch) in prevLevel) {
        val (firstPathPart, restOfThePath) = pathParts.headTail()
        if (restOfThePath.isEmpty()) {
          result[firstPathPart] = branch
        }
        else {
          groups.compute(firstPathPart) { _, currentList ->
            (currentList ?: mutableListOf()) + (restOfThePath to branch)
          }?.let { group -> result[firstPathPart] = group }
        }
      }

      for ((prefix, branchesWithPaths) in groups) {
        result[prefix] = buildSubTree(branchesWithPaths)
      }

      return result
    }

    private fun match(branches: Collection<GitBranch>): MatchResult {
      if (branches.isEmpty() || matcher == null) return MatchResult(branches, null)

      val result = mutableListOf<GitBranch>()
      var topMatch: Pair<GitBranch, Int>? = null

      for (branch in branches) {
        val matchingFragments = matcher.matchingFragments(branch.name)
        if (matchingFragments == null) continue
        result.add(branch)
        val matchingDegree = matcher.matchingDegree(branch.name, false, matchingFragments)
        if (topMatch == null || topMatch.second < matchingDegree) {
          topMatch = branch to matchingDegree
        }
      }

      return MatchResult(result, topMatch)
    }
  }
}
