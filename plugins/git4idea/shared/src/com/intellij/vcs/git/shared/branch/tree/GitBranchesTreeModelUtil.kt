// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.branch.tree

import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.containers.headTail
import com.intellij.util.containers.init
import com.intellij.vcs.git.shared.ref.GitRefUtil
import com.intellij.vcs.git.shared.repo.GitRepositoryModel
import com.intellij.vcs.git.shared.telemetry.GitBranchesPopupSpan
import git4idea.*
import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType
import git4idea.config.GitVcsSettings
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.TreePath

private typealias PathAndRef = Pair<List<String>, GitReference>
private typealias BranchSubtree = Any /* GitBranch | Map<String, BranchSubtree> */

internal class MatchResult<Node>(val matchedNodes: Collection<Node>, val topMatch: Node?)

internal val emptyBranchComparator = Comparator<GitReference> { _, _ -> 0 }

internal fun buildBranchTreeNodes(branchType: GitRefType,
                                  branchesMap: Map<String, Any>,
                                  path: List<String>,
                                  repository: GitRepositoryModel? = null): List<Any> {
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

private fun Map<String, Any>.mapToNodes(branchType: GitRefType, path: List<String>, repository: GitRepositoryModel?): List<Any> {
  return entries.map { (name, value) ->
    if (value is GitReference && repository != null) GitBranchesTreeModel.RefUnderRepository(repository, value)
    else if (value is Map<*, *>) GitBranchesTreeModel.BranchesPrefixGroup(branchType, path + name, repository) else value
  }
}

@ApiStatus.Internal
fun createTreePathFor(model: GitBranchesTreeModel, value: Any): TreePath? {
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
  val isRecent = reference is GitStandardLocalBranch && model.getIndexOfChild(root, GitBranchType.RECENT) != -1
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
                                repositories: List<GitRepositoryModel>,
                                branchNameMatcher: MinusculeMatcher?,
                                localBranchesTree: LazyRefsSubtreeHolder<GitStandardLocalBranch>,
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
  repositories: List<GitRepositoryModel>,
  localBranches: List<GitBranch>
): GitBranch? {
  val repository = repositories.singleOrNull()

  if (repository != null) {
    val root = repository.root?.path
    val recentBranch = if (root != null) GitVcsSettings.getInstance(project).recentBranchesByRepository[root]?.let { recentBranchName ->
      localBranches.find { it.name == recentBranchName }
    } else null

    return recentBranch ?: repository.state.currentBranch
  }
  else {
    return GitVcsSettings.getInstance(project).recentCommonBranch?.let { recentCommonBranch -> localBranches.find { it.name == recentCommonBranch } }
           ?: GitRefUtil.getCommonCurrentBranch(repositories)
  }
}

internal fun getLocalAndRemoteTopLevelNodes(localBranchesTree: LazyRefsSubtreeHolder<GitStandardLocalBranch>,
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
  repositories: List<GitRepositoryModel>,
  matcher: MinusculeMatcher?,
  canHaveChildren: Boolean,
) : LazyHolder<GitBranchesTreeModel.RepositoryNode>(
  repositories.map { GitBranchesTreeModel.RepositoryNode(it, !canHaveChildren) },
  matcher,
  nodeNameSupplier = { it.repository.shortName },
  needFilter = { GitBranchesTreeFilters.byRepositoryName(project) })

@ApiStatus.Internal
class LazyActionsHolder(project: Project, actions: List<Any>, matcher: MinusculeMatcher?) :
  LazyHolder<Any>(actions, matcher,
                  exceptFilter = { it is SeparatorWithText },
                  nodeNameSupplier = { (it as? PopupFactoryImpl.ActionItem)?.text ?: it.toString() },
                  needFilter = { GitBranchesTreeFilters.byActions(project) })

@ApiStatus.Internal
open class LazyHolder<N>(nodes: List<N>,
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

@ApiStatus.Internal
class LazyRefsSubtreeHolder<out T : GitReference>(
  unsortedRefs: Collection<T>,
  matcher: MinusculeMatcher?,
  isPrefixGrouping: () -> Boolean,
  exceptRefFilter: (T) -> Boolean = { false },
  refComparatorGetter: () -> Comparator<GitReference>,
) {
  private val initiallyEmpty = unsortedRefs.isEmpty()

  val sortedValues by lazy { unsortedRefs.sortedWith(refComparatorGetter()) }

  fun isEmpty() = initiallyEmpty || matchingResult.matchedNodes.isEmpty()

  private val matchingResult: MatchResult<T> by lazy {
    match(matcher, sortedValues, GitReference::name, exceptRefFilter)
  }

  val tree: Map<String, Any> by lazy {
    val infoList = matchingResult.matchedNodes
    TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(GitBranchesPopupSpan.BuildingTree.getName()).use { span ->
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
      return LazyRefsSubtreeHolder(emptyList(), null, { false }, { false }, { emptyBranchComparator })
    }
  }
}
