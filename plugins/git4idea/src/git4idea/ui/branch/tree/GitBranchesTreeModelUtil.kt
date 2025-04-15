// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.getCommonCurrentBranch
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsScope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.containers.headTail
import com.intellij.util.containers.init
import com.intellij.vcs.log.Hash
import git4idea.*
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRefUtil
import git4idea.repo.GitRepository
import git4idea.telemetry.GitTelemetrySpan
import git4idea.ui.branch.popup.GitBranchesTreePopupFilterByAction
import git4idea.ui.branch.popup.GitBranchesTreePopupFilterByRepository
import javax.swing.tree.TreePath

private typealias PathAndRef = Pair<List<String>, GitReference>
private typealias BranchSubtree = Any /* GitBranch | Map<String, BranchSubtree> */

internal class MatchResult<Node>(val matchedNodes: Collection<Node>, val topMatch: Node?)

internal val GitRepository.localBranchesOrCurrent get() = branches.localBranches.ifEmpty { currentBranch?.let(::setOf) ?: emptySet() }
internal val GitRepository.recentCheckoutBranches
  get() =
    if (!GitVcsSettings.getInstance(project).showRecentBranches()) emptyList()
    else branches.recentCheckoutBranches.take(Registry.intValue("git.show.recent.checkout.branches"))

internal val GitRepository.tags: Map<GitTag, Hash>
  get() =
    if (!GitVcsSettings.getInstance(project).showTags()) emptyMap()
    else this.tagHolder.getTags()

internal val emptyBranchComparator = Comparator<GitReference> { _, _ -> 0 }

internal fun getRefComparator(
  repositories: List<GitRepository>,
  favoriteBranches: Map<GitRepository, Set<String>>,
  isPrefixGrouping: () -> Boolean
): Comparator<GitReference> {
  return compareBy<GitReference> {
    it.isNotCurrentRef(repositories)
  } then compareBy {
    it.isNotFavorite(favoriteBranches, repositories)
  } then compareBy {
    !(isPrefixGrouping() && it.name.contains('/'))
  } then compareBy(GitReference.REFS_NAMES_COMPARATOR) { it.name }
}

internal fun getSubTreeComparator(favoriteBranches: Map<GitRepository, Set<String>>,
                                  repositories: List<GitRepository>): Comparator<Any> {
  return compareBy<Any> {
    it is GitBranch && it.isNotCurrentRef(repositories) && it.isNotFavorite(favoriteBranches, repositories)
  } then compareBy {
    it is GitBranchesTreeModel.BranchesPrefixGroup
  }
}

private fun GitReference.isNotCurrentRef(repositories: List<GitRepository>) =
  !repositories.any { repo -> GitRefUtil.getCurrentReference(repo) == this }

private fun GitReference.isNotFavorite(favoriteBranches: Map<GitRepository, Set<String>>,
                                       repositories: List<GitRepository>): Boolean {
  return repositories.any { repo -> !(favoriteBranches[repo]?.contains(this.name) ?: false) }
}

internal fun buildBranchTreeNodes(branchType: GitRefType,
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

private fun Map<String, Any>.mapToNodes(branchType: GitRefType, path: List<String>, repository: GitRepository?): List<Any> {
  return entries.map { (name, value) ->
    if (value is GitReference && repository != null) GitBranchesTreeModel.RefUnderRepository(repository, value)
    else if (value is Map<*, *>) GitBranchesTreeModel.BranchesPrefixGroup(branchType, path + name, repository) else value
  }
}

internal fun createTreePathFor(model: GitBranchesTreeModel, value: Any): TreePath? {
  val root = model.root
  val action = value as? PopupFactoryImpl.ActionItem
  if (action != null) {
    return TreePathUtil.convertCollectionToTreePath(listOf(root, action))
  }

  val repositoryNode = value as? GitBranchesTreeModel.RepositoryNode
  if (repositoryNode != null) {
    return TreePathUtil.convertCollectionToTreePath(listOf(root, repositoryNode))
  }

  val typeUnderRepository = value as? GitBranchesTreeModel.RefTypeUnderRepository
  if (typeUnderRepository != null) {
    return TreePathUtil.convertCollectionToTreePath(listOf(root, GitBranchesTreeModel.RepositoryNode(typeUnderRepository.repository, isLeaf = false), typeUnderRepository))
  }

  val refUnderRepository = value as? GitBranchesTreeModel.RefUnderRepository
  val reference = value as? GitReference ?: refUnderRepository?.ref ?: return null
  val isRecent = reference is GitLocalBranch && model.getIndexOfChild(root, GitBranchType.RECENT) != -1
  val refType = GitRefType.of(reference, isRecent)
  val path = mutableListOf<Any>().apply {
    add(root)
    if (refUnderRepository != null) {
      add(GitBranchesTreeModel.RepositoryNode(refUnderRepository.repository, isLeaf = false))
      add(GitBranchesTreeModel.RefTypeUnderRepository(refUnderRepository.repository, refType))
    }
    else {
      add(refType)
    }
  }
  val nameParts = if (model.isPrefixGrouping) reference.name.split('/') else listOf(reference.name)
  val currentPrefix = mutableListOf<String>()
  for (prefixPart in nameParts.init()) {
    currentPrefix.add(prefixPart)
    path.add(GitBranchesTreeModel.BranchesPrefixGroup(refType, currentPrefix.toList(), refUnderRepository?.repository))
  }

  if (refUnderRepository != null) {
    path.add(refUnderRepository)
  }
  else {
    path.add(reference)
  }
  return TreePathUtil.convertCollectionToTreePath(path)
}

@Suppress("UNCHECKED_CAST")
internal fun getPreferredBranch(project: Project,
                                repositories: List<GitRepository>,
                                branchNameMatcher: MinusculeMatcher?,
                                localBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch>,
                                remoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch>,
                                tagsTree: LazyRefsSubtreeHolder<GitTag>,
                                recentBranchesTree: LazyRefsSubtreeHolder<GitReference> = localBranchesTree, ): GitReference? {
  if (branchNameMatcher == null) {
    return getPreferredBranch(project, repositories, localBranchesTree.sortedValues)
  }

  val recentMatch = recentBranchesTree.topMatch as? GitBranch
  val localMatch = localBranchesTree.topMatch as? GitBranch
  val remoteMatch = remoteBranchesTree.topMatch as? GitBranch
  val tagMatch = tagsTree.topMatch

  return recentMatch ?: localMatch ?: remoteMatch ?: tagMatch
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

internal fun getLocalAndRemoteTopLevelNodes(localBranchesTree: LazyRefsSubtreeHolder<GitLocalBranch>,
                                            remoteBranchesTree: LazyRefsSubtreeHolder<GitRemoteBranch>,
                                            tagsTree: LazyRefsSubtreeHolder<GitTag>? = null,
                                            recentCheckoutBranchesTree: LazyRefsSubtreeHolder<GitReference>? = null): List<Any> {
  return listOfNotNull(
    if (recentCheckoutBranchesTree != null && !recentCheckoutBranchesTree.isEmpty()) GitBranchType.RECENT else null,
    if (!localBranchesTree.isEmpty()) GitBranchType.LOCAL else null,
    if (!remoteBranchesTree.isEmpty()) GitBranchType.REMOTE else null,
    if (tagsTree != null && !tagsTree.isEmpty()) GitTagType else null
  )
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

internal open class LazyRepositoryHolder(
  project: Project,
  repositories: List<GitRepository>,
  matcher: MinusculeMatcher?,
  canHaveChildren: Boolean,
) : LazyHolder<GitBranchesTreeModel.RepositoryNode>(
  repositories.map { GitBranchesTreeModel.RepositoryNode(it, canHaveChildren) },
  matcher,
  nodeNameSupplier = { DvcsUtil.getShortRepositoryName(it.repository) },
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

internal class LazyRefsSubtreeHolder<out T: GitReference>(repositories: List<GitRepository>,
                                     unsortedRefs: Collection<T>,
                                     favoriteRefs: Map<GitRepository, Set<String>>,
                                     matcher: MinusculeMatcher?,
                                     isPrefixGrouping: () -> Boolean,
                                     exceptRefFilter: (T) -> Boolean = { false },
                                     refComparatorGetter: () -> Comparator<GitReference> = {
                                       getRefComparator(repositories, favoriteRefs, isPrefixGrouping)
                                     }) {

  private val initiallyEmpty = unsortedRefs.isEmpty()

  val sortedValues by lazy { unsortedRefs.sortedWith(refComparatorGetter()) }

  fun isEmpty() = initiallyEmpty || matchingResult.matchedNodes.isEmpty()

  private val matchingResult: MatchResult<T> by lazy {
    match(matcher, sortedValues, GitReference::name, exceptRefFilter)
  }

  val tree: Map<String, Any> by lazy {
    val infoList = matchingResult.matchedNodes
    getInstance().getTracer(VcsScope).spanBuilder(GitTelemetrySpan.GitBranchesPopup.BuildingTree.getName()).use { span ->
      buildSubTree(infoList.map { (if (isPrefixGrouping()) it.name.split('/') else listOf(it.name)) to it })
    }
  }

  val topMatch: T?
    get() = matchingResult.topMatch

  private fun buildSubTree(prevLevel: List<PathAndRef>): Map<String, BranchSubtree> {
    val result = LinkedHashMap<String, BranchSubtree>()
    val groups = LinkedHashMap<String, MutableList<PathAndRef>>()
    for ((pathParts, branch) in prevLevel) {
      val (firstPathPart, restOfThePath) = pathParts.headTail()
      if (restOfThePath.isEmpty()) {
        result[firstPathPart] = branch
      }
      else {
        val groupChildren = groups.computeIfAbsent(firstPathPart) {
          mutableListOf<PathAndRef>().also {
            // Preserve the order in he LinkedHashMap, it will be overwritten below.
            result[firstPathPart] = emptyMap<String, Any>() // empty BranchSubtree
          }
        }
        groupChildren += (restOfThePath to branch)
      }
    }

    for ((prefix, branchesWithPaths) in groups) {
      result[prefix] = buildSubTree(branchesWithPaths)
    }

    return result
  }

  companion object {
    fun <T: GitReference> emptyHolder(): LazyRefsSubtreeHolder<T> {
      return LazyRefsSubtreeHolder(emptyList(), emptyList(), emptyMap(), null, { false })
    }
  }
}
