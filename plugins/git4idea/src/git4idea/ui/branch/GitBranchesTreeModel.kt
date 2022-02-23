// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ClearableLazyValue
import com.intellij.psi.codeStyle.MinusculeMatcher
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.containers.headTail
import com.intellij.util.containers.init
import com.intellij.util.ui.tree.AbstractTreeModel
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.branch.GitBranchType
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepository
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

private typealias PathAndBranch = Pair<List<String>, GitBranch>

class GitBranchesTreeModel(
  private val project: Project,
  private val repository: GitRepository,
  topLevelItems: List<Any> = emptyList()
) : AbstractTreeModel() {

  private val topLevelNodes = topLevelItems + GitBranchType.LOCAL + GitBranchType.REMOTE

  private val localBranchesTreeValue = ClearableLazyValue.create {
    val branches = repository.branches.localBranches
    branchesMatcher?.let { processMatcherPreference(branches, it) }
    ?: processDefaultPreference(branches)
  }

  private val remoteBranchesTreeValue = ClearableLazyValue.create {
    val branches = repository.branches.remoteBranches
    branchesMatcher?.let { processMatcherPreference(branches, it) }
    ?: BranchesWithMatchPreference(branches.sortedWith(BRANCH_COMPARATOR))
  }

  private val branchesTreeCache = mutableMapOf<Any, List<Any>>()

  var branchesMatcher: MinusculeMatcher? by observable(null) { _, _, _ ->
    branchesTreeCache.keys.removeIf {
      it !is GitRepository
    }
    localBranchesTreeValue.drop()
    remoteBranchesTreeValue.drop()
    treeStructureChanged(TreePath(arrayOf(root, GitBranchType.LOCAL)), null, null)
    treeStructureChanged(TreePath(arrayOf(root, GitBranchType.REMOTE)), null, null)
  }

  private val branchManager by lazy { project.service<GitBranchManager>() }

  private val BRANCH_COMPARATOR = compareBy<GitBranch> {
    !branchManager.isFavorite(GitBranchType.of(it), repository, it.name)
  } then compareBy { it.name }

  private fun processDefaultPreference(branches: Collection<GitLocalBranch>): BranchesWithMatchPreference {
    val sortedBranches = branches.sortedWith(BRANCH_COMPARATOR)
    val recentBranches = GitVcsSettings.getInstance(project).recentBranchesByRepository
    val recentBranch = recentBranches[repository.root.path]?.let { recentBranchName ->
      sortedBranches.find { it.name == recentBranchName }
    }
    if (recentBranch != null) {
      return BranchesWithMatchPreference(sortedBranches, recentBranch to Int.MAX_VALUE)
    }

    val currentBranch = repository.currentBranch
    if (currentBranch != null) {
      return BranchesWithMatchPreference(sortedBranches, currentBranch to Int.MAX_VALUE)
    }

    return BranchesWithMatchPreference(sortedBranches)
  }

  private fun processMatcherPreference(branches: Collection<GitBranch>, matcher: MinusculeMatcher): BranchesWithMatchPreference {
    if (branches.isEmpty()) return BranchesWithMatchPreference(emptyList())

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
    return BranchesWithMatchPreference(result, topMatch)
  }

  private data class BranchesWithMatchPreference(val branches: List<GitBranch>, val preference: Pair<GitBranch, Int>? = null) {

    val tree: Map<String, Any> by lazy {
      buildBranchesTree(branches.map {
        it.name.split('/') to it
      })
    }

    private fun buildBranchesTree(prevLevel: List<PathAndBranch>): Map<String, Any> {
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
          }
        }
      }

      for ((prefix, branchesWithPaths) in groups) {
        result[prefix] = buildBranchesTree(branchesWithPaths)
      }

      return result
    }
  }

  override fun getRoot() = repository

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = (node is GitBranch) || (node is PopupFactoryImpl.ActionItem)

  private fun getChildren(parent: Any?): List<Any> {
    if (parent == null) return emptyList()
    return when (parent) {
      is GitRepository -> topLevelNodes
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is BranchesPrefixGroup -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent.type, parent.prefix) }
      else -> emptyList()
    }
  }

  private fun getBranchTreeNodes(branchType: GitBranchType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when (branchType) {
      GitBranchType.LOCAL -> localBranchesTreeValue.value.tree
      GitBranchType.REMOTE -> remoteBranchesTreeValue.value.tree
    }

    if (path.isEmpty()) {
      return branchesMap.mapToNodes(branchType, path)
    }
    else {
      var currentLevel = branchesMap
      for (prefixPart in path) {
        @Suppress("UNCHECKED_CAST")
        currentLevel = (currentLevel[prefixPart] as? Map<String, Any>) ?: return emptyList()
      }
      return currentLevel.mapToNodes(branchType, path)
    }
  }

  private fun Map<String, Any>.mapToNodes(branchType: GitBranchType, path: List<String>): List<Any> {
    return entries.map { (name, value) ->
      if (value is Map<*, *>) BranchesPrefixGroup(branchType, path + name) else value
    }
  }

  fun getPreferredSelection(): TreePath? {
    val localPreference = localBranchesTreeValue.value.preferFirstIfNoPreference()
    val remotePreference = remoteBranchesTreeValue.value.preferFirstIfNoPreference()
    if (localPreference == null && remotePreference == null) return null
    if (localPreference != null && remotePreference == null) return createTreePathFor(localPreference.first)
    if (localPreference == null && remotePreference != null) return createTreePathFor(remotePreference.first)

    if (localPreference!!.second >= remotePreference!!.second) {
      return createTreePathFor(localPreference.first)
    }
    else {
      return createTreePathFor(remotePreference.first)
    }
  }

  private fun BranchesWithMatchPreference.preferFirstIfNoPreference() =
    let { (branches, preference) ->
      when {
        preference != null -> preference
        branches.isNotEmpty() -> branches.first() to Int.MIN_VALUE
        else -> null
      }
    }

  private fun createTreePathFor(branch: GitBranch): TreePath {
    val branchType = GitBranchType.of(branch)
    val path = mutableListOf<Any>().apply {
      add(root)
      add(branchType)
    }
    val nameParts = branch.name.split('/')
    val currentPrefix = mutableListOf<String>()
    for (prefixPart in nameParts.init()) {
      currentPrefix.add(prefixPart)
      path.add(BranchesPrefixGroup(branchType, currentPrefix.toList()))
    }

    path.add(branch)
    return TreePathUtil.convertCollectionToTreePath(path)
  }

  data class BranchesPrefixGroup(val type: GitBranchType, val prefix: List<String>)
}