// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.branch.BranchType
import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.containers.headTail
import com.intellij.util.containers.init
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import javax.swing.tree.TreePath

private typealias PathAndBranch = Pair<List<String>, GitBranch>
private typealias MatchResult = Pair<Collection<GitBranch>, GitBranch?>

internal val GitRepository.localBranchesOrCurrent get() = branches.localBranches.ifEmpty { currentBranch?.let(::setOf) ?: emptySet() }
internal val GitRepository.recentCheckoutBranches
  get() =
    if (!GitVcsSettings.getInstance(project).showRecentBranches()) emptyList()
    else branches.recentCheckoutBranches.take(Registry.intValue("git.show.recent.checkout.branches"))

internal val emptyBranchComparator = Comparator<GitBranch> { _, _ -> 0 }

private fun getBranchComparator(repositories: List<GitRepository>, isPrefixGrouping: () -> Boolean) = compareBy<GitBranch> {
  it.isNotCurrentBranch(repositories)
} then compareBy {
  it.isNotFavorite(repositories)
} then compareBy {
  !(isPrefixGrouping() && it.name.contains('/'))
} then compareBy { it.name }

internal fun getSubTreeComparator(repositories: List<GitRepository>) = compareBy<Any> {
  it is GitBranch && it.isNotCurrentBranch(repositories) && it.isNotFavorite(repositories)
} then compareBy {
  it is GitBranchesTreeModel.BranchesPrefixGroup
}

private fun GitBranch.isNotCurrentBranch(repositories: List<GitRepository>) =
  !repositories.any { repo -> repo.currentBranch == this }

private fun GitBranch.isNotFavorite(repositories: List<GitRepository>) =
  !repositories.all { repo -> repo.project.service<GitBranchManager>().isFavorite(GitBranchType.of(this), repo, name) }

internal fun buildBranchTreeNodes(branchType: BranchType,
                                  branchesMap: Map<String, Any>,
                                  path: List<String>,
                                  repository: GitRepository? = null): List<Any> {
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

private fun Map<String, Any>.mapToNodes(branchType: BranchType, path: List<String>, repository: GitRepository?): List<Any> {
  return entries.map { (name, value) ->
    if (value is GitBranch && repository != null) GitBranchesTreeModel.BranchUnderRepository(repository, value)
    else if (value is Map<*, *>) GitBranchesTreeModel.BranchesPrefixGroup(branchType, path + name, repository) else value
  }
}

internal fun createTreePathFor(model: GitBranchesTreeModel, value: Any): TreePath? {
  val root = model.root
  val repository = value as? GitRepository
  if (repository != null) {
    return TreePathUtil.convertCollectionToTreePath(listOf(root, repository))
  }

  val typeUnderRepository = value as? GitBranchesTreeModel.BranchTypeUnderRepository
  if (typeUnderRepository != null) {
    return TreePathUtil.convertCollectionToTreePath(listOf(root, typeUnderRepository.repository, typeUnderRepository))
  }

  val branchUnderRepository = value as? GitBranchesTreeModel.BranchUnderRepository
  val branch = value as? GitBranch ?: branchUnderRepository?.branch ?: return null
  val isRecent = branch is GitLocalBranch && model.getIndexOfChild(root, GitBranchesTreeModel.RecentNode) != -1
  val branchType = if (isRecent) GitBranchesTreeModel.RecentNode else GitBranchType.of(branch)
  val path = mutableListOf<Any>().apply {
    add(root)
    if (branchUnderRepository != null) {
      add(branchUnderRepository.repository)
      add(GitBranchesTreeModel.BranchTypeUnderRepository(branchUnderRepository.repository, branchType))
    }
    else {
      add(branchType)
    }
  }
  val nameParts = if (model.isPrefixGrouping) branch.name.split('/') else listOf(branch.name)
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

internal fun getPreferredBranch(project: Project,
                                repositories: List<GitRepository>,
                                branchNameMatcher: MinusculeMatcher?,
                                localBranchesTree: LazyBranchesSubtreeHolder,
                                remoteBranchesTree: LazyBranchesSubtreeHolder,
                                recentBranchesTree: LazyBranchesSubtreeHolder = localBranchesTree): GitBranch? {
  if (branchNameMatcher == null) {
    if (repositories.size == 1) {
      val repository = repositories.single()
      val recentBranches = GitVcsSettings.getInstance(project).recentBranchesByRepository
      val recentBranch = recentBranches[repository.root.path]?.let { recentBranchName ->
        repository.recentCheckoutBranches.find { it.name == recentBranchName }
        ?: localBranchesTree.branches.find { it.name == recentBranchName }
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
        ?.let { recentOrCommonBranchName ->
          localBranchesTree.branches.find { it.name == recentOrCommonBranchName }
        }
      return branch
    }

  }

  val recentMatch = recentBranchesTree.topMatch
  val localMatch = localBranchesTree.topMatch
  val remoteMatch = remoteBranchesTree.topMatch

  return recentMatch ?: localMatch ?: remoteMatch
}

internal fun getLocalAndRemoteTopLevelNodes(localBranchesTree: LazyBranchesSubtreeHolder,
                                            remoteBranchesTree: LazyBranchesSubtreeHolder,
                                            recentCheckoutBranchesTree: LazyBranchesSubtreeHolder? = null): List<Any> {
  return listOfNotNull(
    if (recentCheckoutBranchesTree != null && !recentCheckoutBranchesTree.isEmpty()) GitBranchesTreeModel.RecentNode else null,
    if (!localBranchesTree.isEmpty()) GitBranchType.LOCAL else null,
    if (!remoteBranchesTree.isEmpty()) GitBranchType.REMOTE else null
  )
}

internal class LazyBranchesSubtreeHolder(
  unsortedBranches: Collection<GitBranch>,
  repositories: List<GitRepository>,
  private val matcher: MinusculeMatcher?,
  private val isPrefixGrouping: () -> Boolean,
  private val exceptBranchFilter: (GitBranch) -> Boolean = { false },
  private val branchComparatorGetter: () -> Comparator<GitBranch> = { getBranchComparator(repositories, isPrefixGrouping) }
) {

  private val initiallyEmpty = unsortedBranches.isEmpty()

  val branches by lazy { unsortedBranches.sortedWith(branchComparatorGetter()) }

  fun isEmpty() = initiallyEmpty || matchingResult.first.isEmpty()

  private val matchingResult: MatchResult by lazy {
    match(branches)
  }

  val tree: Map<String, Any> by lazy {
    val branchesList = matchingResult.first
    buildSubTree(branchesList.map { (if (isPrefixGrouping()) it.name.split('/') else listOf(it.name)) to it })
  }

  val topMatch: GitBranch?
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
      if (exceptBranchFilter(branch)) continue

      val matchingFragments = matcher.matchingFragments(branch.name)
      if (matchingFragments == null) continue
      result.add(branch)
      val matchingDegree = matcher.matchingDegree(branch.name, false, matchingFragments)
      if (topMatch == null || topMatch.second < matchingDegree) {
        topMatch = branch to matchingDegree
      }
    }

    return MatchResult(result, topMatch?.first)
  }
}
