// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.branch.BranchType
import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsScope
import com.intellij.openapi.vcs.telemetry.VcsTelemetrySpan
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.computeWithSpan
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.containers.headTail
import com.intellij.util.containers.init
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import git4idea.ui.branch.popup.GitBranchesTreePopupFilterByAction
import git4idea.ui.branch.popup.GitBranchesTreePopupFilterByRepository
import javax.swing.tree.TreePath

private typealias PathAndBranch = Pair<List<String>, GitBranch>

internal class MatchResult<Node>(val matchedNodes: Collection<Node>, val topMatch: Node?)

internal val GitRepository.localBranchesOrCurrent get() = branches.localBranches.ifEmpty { currentBranch?.let(::setOf) ?: emptySet() }
internal val GitRepository.recentCheckoutBranches
  get() =
    if (!GitVcsSettings.getInstance(project).showRecentBranches()) emptyList()
    else branches.recentCheckoutBranches.take(Registry.intValue("git.show.recent.checkout.branches"))

internal val emptyBranchComparator = Comparator<GitBranch> { _, _ -> 0 }

internal fun getBranchComparator(
  repositories: List<GitRepository>,
  favoriteBranches: Map<GitRepository, Set<String>>,
  isPrefixGrouping: () -> Boolean
): Comparator<GitBranch> {
  return compareBy<GitBranch> {
    it.isNotCurrentBranch(repositories)
  } then compareBy {
    it.isNotFavorite(favoriteBranches, repositories)
  } then compareBy {
    !(isPrefixGrouping() && it.name.contains('/'))
  } then compareBy { it.name }
}

internal fun getSubTreeComparator(favoriteBranches: Map<GitRepository, Set<String>>,
                                  repositories: List<GitRepository>): Comparator<Any> {
  return compareBy<Any> {
    it is GitBranch && it.isNotCurrentBranch(repositories) && it.isNotFavorite(favoriteBranches, repositories)
  } then compareBy {
    it is GitBranchesTreeModel.BranchesPrefixGroup
  }
}

private fun GitBranch.isNotCurrentBranch(repositories: List<GitRepository>) =
  !repositories.any { repo -> repo.currentBranch == this }

private fun GitBranch.isNotFavorite(favoriteBranches: Map<GitRepository, Set<String>>,
                                    repositories: List<GitRepository>): Boolean {
  return repositories.any { repo -> !(favoriteBranches[repo]?.contains(this.name) ?: false) }
}

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
  val action = value as? PopupFactoryImpl.ActionItem
  if (action != null) {
    return TreePathUtil.convertCollectionToTreePath(listOf(root, action))
  }

  val topRepository = value as? GitBranchesTreeModel.TopLevelRepository
  if (topRepository != null) {
    return TreePathUtil.convertCollectionToTreePath(listOf(root, topRepository))
  }

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
    return getPreferredBranch(project, repositories, localBranchesTree.branches)
  }

  val recentMatch = recentBranchesTree.topMatch
  val localMatch = localBranchesTree.topMatch
  val remoteMatch = remoteBranchesTree.topMatch

  return recentMatch ?: localMatch ?: remoteMatch
}

internal fun getPreferredBranch(
  project: Project,
  repositories: List<GitRepository>,
  localBranches: List<GitBranch>
): GitBranch? {
  if (repositories.size == 1) {
    val repository = repositories.single()
    val recentBranches = GitVcsSettings.getInstance(project).recentBranchesByRepository
    val recentBranch = recentBranches[repository.root.path]?.let { recentBranchName ->
      repository.recentCheckoutBranches.find { it.name == recentBranchName }
      ?: localBranches.find { it.name == recentBranchName }
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
        localBranches.find { it.name == recentOrCommonBranchName }
      }
    return branch
  }
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

internal fun matchBranches(
  matcher: MinusculeMatcher?,
  branches: Collection<GitBranch>,
  exceptBranchFilter: (GitBranch) -> Boolean = { false }
): MatchResult<GitBranch> {
  return match(matcher, branches, GitBranch::getName, exceptBranchFilter)
}

internal fun <N> match(
  matcher: MinusculeMatcher?,
  nodes: Collection<N>,
  nodeNameSupplier: (N) -> String = { it.toString() },
  exceptFilter: (N) -> Boolean = { false }
): MatchResult<N> {
  if (nodes.isEmpty() || matcher == null) return MatchResult(nodes, null)

  val result = mutableListOf<N>()
  var topMatch: Pair<N, Int>? = null
  for (node in nodes) {
    if (exceptFilter(node)) continue

    val name = nodeNameSupplier(node)
    val matchingFragments = matcher.matchingFragments(name)
    if (matchingFragments == null) continue

    result.add(node)
    val matchingDegree = matcher.matchingDegree(name, false, matchingFragments)
    if (topMatch == null || topMatch.second < matchingDegree) {
      topMatch = node to matchingDegree
    }
  }

  return MatchResult(result, topMatch?.first)
}

internal fun addSeparatorIfNeeded(nodes: Collection<Any>, separator: SeparatorWithText): MutableCollection<Any> {
  val result = (nodes as? MutableCollection<Any>) ?: nodes.toMutableList()
  if (nodes.isNotEmpty() && nodes.last() !is SeparatorWithText) {
    result.add(separator)
  }
  return result
}

internal open class LazyRepositoryHolder(project: Project,
                                         repositories: List<GitRepository>, matcher: MinusculeMatcher?) :
  LazyHolder<GitRepository>(repositories, matcher, nodeNameSupplier = DvcsUtil::getShortRepositoryName,
                            needFilter = { GitBranchesTreePopupFilterByRepository.isSelected(project) })

internal class LazyActionsHolder(project: Project, actions: List<Any>, matcher: MinusculeMatcher?) :
  LazyHolder<Any>(actions, matcher,
                  exceptFilter = { it is SeparatorWithText },
                  nodeNameSupplier = { (it as? PopupFactoryImpl.ActionItem)?.text ?: it.toString() },
                  needFilter = { GitBranchesTreePopupFilterByAction.isSelected(project) })

internal open class LazyHolder<N>(nodes: List<N>,
                                  matcher: MinusculeMatcher?,
                                  exceptFilter: (N) -> Boolean = { false },
                                  nodeNameSupplier: (N) -> String = { it.toString() },
                                  private val needFilter: () -> Boolean = { matcher != null }) {

  private val initiallyEmpty = nodes.isEmpty()

  private val matchingResult: MatchResult<N> by lazy {
    match(if (needFilter()) matcher else null,
          nodes, nodeNameSupplier, exceptFilter)
  }

  val topMatch: N?
    get() = matchingResult.topMatch

  val match: Collection<N>
    get() = matchingResult.matchedNodes

  fun isEmpty() = initiallyEmpty || !needFilter() || match.isEmpty()
}

internal class LazyBranchesSubtreeHolder(
  repositories: List<GitRepository>,
  unsortedBranches: Collection<GitBranch>,
  favoriteBranches: Map<GitRepository, Set<String>>,
  private val matcher: MinusculeMatcher?,
  private val isPrefixGrouping: () -> Boolean,
  private val exceptBranchFilter: (GitBranch) -> Boolean = { false },
  private val branchComparatorGetter: () -> Comparator<GitBranch> = {
    getBranchComparator(repositories, favoriteBranches, isPrefixGrouping)
  }
) {

  private val initiallyEmpty = unsortedBranches.isEmpty()

  val branches by lazy { unsortedBranches.sortedWith(branchComparatorGetter()) }

  fun isEmpty() = initiallyEmpty || matchingResult.matchedNodes.isEmpty()

  private val matchingResult: MatchResult<GitBranch> by lazy {
    matchBranches(matcher, branches, exceptBranchFilter)
  }

  val tree: Map<String, Any> by lazy {
    val branchesList = matchingResult.matchedNodes
    computeWithSpan(getInstance().getTracer(VcsScope), VcsTelemetrySpan.GitBranchesPopup.BuildingTree.getName()) {
      buildSubTree(branchesList.map { (if (isPrefixGrouping()) it.name.split('/') else listOf(it.name)) to it })
    }
  }

  val topMatch: GitBranch?
    get() = matchingResult.topMatch

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
}
