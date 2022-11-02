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
import git4idea.ui.branch.GitBranchesTreeModel.TreeRoot
import javax.swing.tree.TreePath
import kotlin.properties.Delegates.observable

private typealias PathAndBranch = Pair<List<String>, GitBranch>
private typealias MatchResult = Pair<Collection<GitBranch>, Pair<GitBranch, Int>?>

class GitBranchesTreeModelImpl(
  private val project: Project,
  private val repositories: List<GitRepository>,
  private val topLevelActions: List<Any> = emptyList()
) : AbstractTreeModel(), GitBranchesTreeModel {

  private val branchesSubtreeSeparator = GitBranchesTreePopup.createTreeSeparator()

  private val branchManager = project.service<GitBranchManager>()
  private val branchComparator = compareBy<GitBranch> {
    !repositories.any { repo -> repo.currentBranch == it }
  } then compareBy {
    !repositories.all { repo -> branchManager.isFavorite(GitBranchType.of(it), repo, it.name) }
  } then compareBy { !(isPrefixGrouping && it.name.contains('/')) } then compareBy { it.name }

  private lateinit var localBranchesTree: LazyBranchesSubtreeHolder
  private lateinit var remoteBranchesTree: LazyBranchesSubtreeHolder

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
    treeStructureChanged(TreePath(arrayOf(root)), null, null)
  }

  override fun getRoot() = TreeRoot

  override fun getChild(parent: Any?, index: Int): Any = getChildren(parent)[index]

  override fun getChildCount(parent: Any?): Int = getChildren(parent).size

  override fun getIndexOfChild(parent: Any?, child: Any?): Int = getChildren(parent).indexOf(child)

  override fun isLeaf(node: Any?): Boolean = node is GitBranch
                                             || (node === GitBranchType.LOCAL && localBranchesTree.isEmpty())
                                             || (node === GitBranchType.REMOTE && remoteBranchesTree.isEmpty())

  private fun getChildren(parent: Any?): List<Any> {
    if (parent == null || !haveFilteredBranches()) return emptyList()
    return when (parent) {
      TreeRoot -> getTopLevelNodes()
      is GitBranchType -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent, emptyList()) }
      is GitBranchesTreeModel.BranchesPrefixGroup -> branchesTreeCache.getOrPut(parent) { getBranchTreeNodes(parent.type, parent.prefix) }
      else -> emptyList()
    }
  }

  private fun getTopLevelNodes(): List<Any> {
    val repositoriesOrEmpty = if (repositories.size > 1) repositories + branchesSubtreeSeparator else emptyList()

    return if (branchTypeFilter != null) topLevelActions + repositoriesOrEmpty + branchTypeFilter!!
    else topLevelActions + repositoriesOrEmpty + GitBranchType.LOCAL + GitBranchType.REMOTE
  }

  private fun getBranchTreeNodes(branchType: GitBranchType, path: List<String>): List<Any> {
    val branchesMap: Map<String, Any> = when (branchType) {
      GitBranchType.LOCAL -> localBranchesTree.tree
      GitBranchType.REMOTE -> remoteBranchesTree.tree
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
      if (value is Map<*, *>) GitBranchesTreeModel.BranchesPrefixGroup(branchType, path + name) else value
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

  private fun createTreePathFor(branch: GitBranch): TreePath {
    val branchType = GitBranchType.of(branch)
    val path = mutableListOf<Any>().apply {
      add(root)
      add(branchType)
    }
    val nameParts = if (isPrefixGrouping) branch.name.split('/') else listOf(branch.name)
    val currentPrefix = mutableListOf<String>()
    for (prefixPart in nameParts.init()) {
      currentPrefix.add(prefixPart)
      path.add(GitBranchesTreeModel.BranchesPrefixGroup(branchType, currentPrefix.toList()))
    }

    path.add(branch)
    return TreePathUtil.convertCollectionToTreePath(path)
  }

  override fun filterBranches(type: GitBranchType?, matcher: MinusculeMatcher?) {
    branchTypeFilter = type
    branchNameMatcher = matcher
  }

  private fun haveFilteredBranches(): Boolean = !localBranchesTree.isEmpty() || !remoteBranchesTree.isEmpty()

  private inner class LazyBranchesSubtreeHolder(
    val branches: Collection<GitBranch>,
    comparator: Comparator<GitBranch>,
    private val matcher: MinusculeMatcher? = null
  ) {

    fun isEmpty() = matchingResult.first.isEmpty()

    private val matchingResult: MatchResult by lazy {
      match(branches)
    }

    val tree: Map<String, Any> by lazy {
      val branchesList = matchingResult.first.sortedWith(comparator)
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
