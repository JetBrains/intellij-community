// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UnorderedPair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.Stack
import com.intellij.vcs.log.data.index.VcsLogPathsIndex
import com.intellij.vcs.log.data.index.VcsLogPathsIndex.ChangeKind
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph
import com.intellij.vcs.log.graph.impl.facade.*
import com.intellij.vcs.log.graph.utils.*
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import gnu.trove.*
import java.util.*
import java.util.function.BiConsumer

internal class FileHistoryBuilder(private val startCommit: Int?,
                                  private val startPath: FilePath,
                                  private val fileNamesData: FileNamesData) : BiConsumer<LinearGraphController, PermanentGraphInfo<Int>> {
  val pathsMap = mutableMapOf<Int, MaybeDeletedFilePath>()

  override fun accept(controller: LinearGraphController, permanentGraphInfo: PermanentGraphInfo<Int>) {
    val needToRepeat = removeTrivialMerges(controller, permanentGraphInfo, fileNamesData, this::reportTrivialMerges)

    pathsMap.putAll(refine(controller, startCommit, permanentGraphInfo))

    if (needToRepeat) {
      LOG.info("Some merge commits were not excluded from file history for ${startPath.path}")
      removeTrivialMerges(controller, permanentGraphInfo, fileNamesData, this::reportTrivialMerges)
    }
  }

  private fun reportTrivialMerges(trivialMerges: Set<Int>) {
    LOG.debug("Excluding ${trivialMerges.size} trivial merges from history for ${startPath.path}")
  }

  private fun refine(controller: LinearGraphController,
                     startCommit: Int?,
                     permanentGraphInfo: PermanentGraphInfo<Int>): Map<Int, MaybeDeletedFilePath> {
    if (fileNamesData.hasRenames && Registry.`is`("vcs.history.refine")) {
      val visibleLinearGraph = controller.compiledGraph

      val (row, path) = startCommit?.let {
        findAncestorRowAffectingFile(startCommit, visibleLinearGraph, permanentGraphInfo)
      } ?: Pair(0, MaybeDeletedFilePath(startPath))
      if (row >= 0) {
        val refiner = FileHistoryRefiner(visibleLinearGraph, permanentGraphInfo, fileNamesData)
        val (paths, excluded) = refiner.refine(row, path)
        if (!excluded.isEmpty()) {
          LOG.info("Excluding ${excluded.size} commits from history for ${startPath.path}")
          val hidden = hideCommits(controller, permanentGraphInfo, excluded)
          if (!hidden) LOG.error("Could not hide excluded commits from history for ${startPath.path}")
        }
        return paths
      }
    }
    return fileNamesData.buildPathsMap()
  }

  private fun findAncestorRowAffectingFile(commitId: Int,
                                           visibleLinearGraph: LinearGraph,
                                           permanentGraphInfo: PermanentGraphInfo<Int>): Pair<Int, MaybeDeletedFilePath> {
    val existing = MaybeDeletedFilePath(startPath)
    val deleted = MaybeDeletedFilePath(startPath, true)
    val isDeleted: Ref<Boolean> = Ref.create(false)
    val row = findVisibleAncestorRow(commitId, visibleLinearGraph, permanentGraphInfo) { nodeId ->
      val id = permanentGraphInfo.permanentCommitsInfo.getCommitId(nodeId)
      when {
        fileNamesData.affects(id, existing) -> true
        fileNamesData.affects(id, deleted) -> {
          isDeleted.set(true)
          true
        }
        else -> false
      }
    } ?: -1
    return Pair(row, if (isDeleted.get()) deleted else existing)
  }

  companion object {
    private val LOG = Logger.getInstance(FileHistoryBuilder::class.java)
  }
}

fun removeTrivialMerges(controller: LinearGraphController,
                        permanentGraphInfo: PermanentGraphInfo<Int>,
                        fileNamesData: FileNamesData,
                        report: (Set<Int>) -> Unit): Boolean {
  val trivialCandidates = TIntHashSet()
  val nonTrivialMerges = TIntHashSet()
  fileNamesData.forEach { _, commit, changes ->
    if (changes.size() > 1) {
      if (changes.containsValue(ChangeKind.NOT_CHANGED)) {
        trivialCandidates.add(commit)
      }
      else {
        nonTrivialMerges.add(commit)
      }
    }
  }
  // since this code can be executed before refine, there can be commits with several files changed in them
  // if several files are changed in the merge commit, it can be trivial for one file, but not trivial for the other
  // in this case we may need to repeat the process after refine
  val needToRepeat = trivialCandidates.removeAll(nonTrivialMerges)

  if (!trivialCandidates.isEmpty) {
    modifyGraph(controller) { collapsedGraph ->
      val trivialMerges = hideTrivialMerges(collapsedGraph) { nodeId: Int ->
        trivialCandidates.contains(permanentGraphInfo.permanentCommitsInfo.getCommitId(nodeId))
      }
      if (trivialMerges.isNotEmpty()) report(trivialMerges)
      fileNamesData.removeAll(trivialMerges.map { permanentGraphInfo.permanentCommitsInfo.getCommitId(it) })
    }
  }

  return needToRepeat
}

internal fun hideTrivialMerges(collapsedGraph: CollapsedGraph,
                               isCandidateNodeId: (Int) -> Boolean): Set<Int> {
  val result = mutableSetOf<Int>()
  val graph = LinearGraphUtils.asLiteLinearGraph(collapsedGraph.compiledGraph)

  outer@ for (v in graph.nodesCount() - 1 downTo 0) {
    val nodeId = collapsedGraph.compiledGraph.getNodeId(v)
    if (isCandidateNodeId(nodeId)) {
      val downNodes = graph.getNodes(v, LiteLinearGraph.NodeFilter.DOWN)
      if (downNodes.size == 1) {
        result.add(nodeId)
        hideTrivialMerge(collapsedGraph, graph, v, downNodes.single())
      }
      else if (downNodes.size >= 2) {
        val sortedParentsIt = downNodes.sortedDescending().iterator()
        var currentParent = sortedParentsIt.next()
        while (sortedParentsIt.hasNext()) {
          val nextParent = sortedParentsIt.next()
          if (!graph.isAncestor(currentParent, nextParent)) continue@outer
          currentParent = nextParent
        }
        result.add(nodeId)
        hideTrivialMerge(collapsedGraph, graph, v, currentParent)
      }
    }
  }

  return result
}

private fun hideTrivialMerge(collapsedGraph: CollapsedGraph, graph: LiteLinearGraph, node: Int, singleParent: Int) {
  collapsedGraph.modify {
    hideRow(node)
    for (upNode in graph.getNodes(node, LiteLinearGraph.NodeFilter.UP)) {
      connectRows(upNode, singleParent)
    }
  }
}

internal class FileHistoryRefiner(private val visibleLinearGraph: LinearGraph,
                                  permanentGraphInfo: PermanentGraphInfo<Int>,
                                  private val namesData: FileNamesData) : Dfs.NodeVisitor {
  private val permanentCommitsInfo: PermanentCommitsInfo<Int> = permanentGraphInfo.permanentCommitsInfo
  private val permanentLinearGraph: LiteLinearGraph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)

  private val paths = Stack<MaybeDeletedFilePath>()
  private val visibilityBuffer = BitSetFlags(permanentLinearGraph.nodesCount()) // a reusable buffer for bfs
  private val pathsForCommits = ContainerUtil.newHashMap<Int, MaybeDeletedFilePath>()
  private val excluded = ContainerUtil.newHashSet<Int>()

  fun refine(row: Int, startPath: MaybeDeletedFilePath): Pair<HashMap<Int, MaybeDeletedFilePath>, HashSet<Int>> {
    paths.push(startPath)
    LinearGraphUtils.asLiteLinearGraph(visibleLinearGraph).walk(row, this)

    pathsForCommits.forEach { commit, path ->
      if (path != null && !namesData.affects(commit, path, true)) {
        excluded.add(commit)
      }
    }

    excluded.forEach { pathsForCommits.remove(it) }
    return Pair(pathsForCommits, excluded)
  }

  override fun enterNode(currentNode: Int, previousNode: Int, down: Boolean) {
    val currentNodeId = visibleLinearGraph.getNodeId(currentNode)
    val currentCommit = permanentCommitsInfo.getCommitId(currentNodeId)

    val previousPath = paths.last()
    var currentPath: MaybeDeletedFilePath = previousPath

    if (previousNode != Dfs.NextNode.NODE_NOT_FOUND) {
      val previousNodeId = visibleLinearGraph.getNodeId(previousNode)
      val previousCommit = permanentCommitsInfo.getCommitId(previousNodeId)

      currentPath = if (down) {
        val pathGetter = { parentIndex: Int ->
          namesData.getPathInParentRevision(previousCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
        }
        val path = findPathWithoutConflict(previousNodeId, pathGetter)
        path ?: pathGetter(permanentLinearGraph.getCorrespondingParent(previousNodeId, currentNodeId, visibilityBuffer))
      }
      else {
        val pathGetter = { parentIndex: Int ->
          namesData.getPathInChildRevision(currentCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
        }
        val path = findPathWithoutConflict(currentNodeId, pathGetter)
        // since in reality there is no edge between the nodes, but the whole path, we need to know, which parent is affected by this path
        path ?: pathGetter(permanentLinearGraph.getCorrespondingParent(currentNodeId, previousNodeId, visibilityBuffer))
      }
    }

    pathsForCommits[currentCommit] = currentPath
    paths.push(currentPath)
  }

  private fun findPathWithoutConflict(nodeId: Int, pathGetter: (Int) -> MaybeDeletedFilePath): MaybeDeletedFilePath? {
    val parents = permanentLinearGraph.getNodes(nodeId, LiteLinearGraph.NodeFilter.DOWN)
    val path = pathGetter(parents.first())
    if (parents.size == 1) return path

    if (parents.subList(1, parents.size).find { pathGetter(it) != path } != null) return null
    return path
  }

  override fun exitNode(node: Int) {
    paths.pop()
  }
}

abstract class FileNamesData(startPaths: Collection<FilePath>) {
  // file -> (commitId -> (parent commitId -> change kind))
  private val affectedCommits = THashMap<FilePath, TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>>(FILE_PATH_HASHING_STRATEGY)
  private val commitToRename = MultiMap.createSmart<UnorderedPair<Int>, Rename>()

  val isEmpty: Boolean
    get() = affectedCommits.isEmpty
  val hasRenames: Boolean
    get() = !commitToRename.isEmpty
  val files: Set<FilePath>
    get() = affectedCommits.keys

  constructor(startPath: FilePath) : this(listOf(startPath))

  init {
    val newPaths = THashSet<FilePath>(FILE_PATH_HASHING_STRATEGY)
    newPaths.addAll(startPaths)

    while (newPaths.isNotEmpty()) {
      val commits = THashMap<FilePath, TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>>(FILE_PATH_HASHING_STRATEGY)
      newPaths.associateTo(commits) { kotlin.Pair(it, getAffectedCommits(it)) }
      affectedCommits.putAll(commits)
      newPaths.clear()

      collectAdditionsDeletions(commits) { ad ->
        if (commitToRename[ad.commits].any { rename -> ad.matches(rename) }) return@collectAdditionsDeletions
        findRename(ad.parent, ad.child, ad::matches)?.let { files ->
          val rename = Rename(files.first, files.second, ad.parent, ad.child)
          commitToRename.putValue(ad.commits, rename)
          val otherPath = rename.getOtherPath(ad)!!
          if (!affectedCommits.containsKey(otherPath)) {
            newPaths.add(otherPath)
          }
        }
      }
    }
  }

  private fun collectAdditionsDeletions(commits: Map<FilePath, TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>>,
                                        action: (AdditionDeletion) -> Unit) {
    commits.forEach { path, commit, changes ->
      changes.forEachEntry { parent, change ->
        createAdditionDeletion(parent, commit, change, path)?.let { ad -> action(ad) }
        true
      }
    }
  }

  private fun createAdditionDeletion(parent: Int, commit: Int, change: ChangeKind, path: FilePath): AdditionDeletion? {
    if (parent != commit && (change == ChangeKind.ADDED || change == ChangeKind.REMOVED)) {
      return AdditionDeletion(path, commit, parent, change == ChangeKind.ADDED)
    }
    return null
  }

  fun getPathInParentRevision(commit: Int, parent: Int, childPath: MaybeDeletedFilePath): MaybeDeletedFilePath {
    val childFilePath = childPath.filePath
    val changeKind = affectedCommits[childFilePath]?.get(commit)?.get(parent) ?: return childPath
    if (changeKind == ChangeKind.NOT_CHANGED) return childPath

    val renames = commitToRename.get(UnorderedPair(commit, parent))
    if (!childPath.deleted) {
      val otherPath = renames.firstNotNull { rename -> rename.getOtherPath(commit, childFilePath) }
      if (otherPath != null) return MaybeDeletedFilePath(otherPath)
      return MaybeDeletedFilePath(childFilePath, changeKind == ChangeKind.ADDED)
    }

    if (changeKind == ChangeKind.REMOVED) {
      // checking if this is actually an unrelated rename
      if (renames.firstNotNull { rename -> rename.getOtherPath(parent, childFilePath) } != null) return childPath
    }
    return MaybeDeletedFilePath(childFilePath, changeKind != ChangeKind.REMOVED)
  }

  fun getPathInChildRevision(commit: Int, parent: Int, parentPath: MaybeDeletedFilePath): MaybeDeletedFilePath {
    val parentFilePath = parentPath.filePath
    val changeKind = affectedCommits[parentFilePath]?.get(commit)?.get(parent) ?: return parentPath
    if (changeKind == ChangeKind.NOT_CHANGED) return parentPath

    val renames = commitToRename.get(UnorderedPair(commit, parent))
    if (!parentPath.deleted) {
      val otherPath = renames.firstNotNull { rename -> rename.getOtherPath(parent, parentFilePath) }
      if (otherPath != null) return MaybeDeletedFilePath(otherPath)
      return MaybeDeletedFilePath(parentFilePath, changeKind == ChangeKind.REMOVED)
    }

    if (changeKind == ChangeKind.ADDED) {
      // checking if this is actually an unrelated rename
      if (renames.firstNotNull { rename -> rename.getOtherPath(commit, parentFilePath) } != null) return parentPath
    }
    return MaybeDeletedFilePath(parentFilePath, changeKind != ChangeKind.ADDED)
  }

  fun affects(commit: Int, path: MaybeDeletedFilePath, verify: Boolean = false): Boolean {
    val changes = affectedCommits[path.filePath]?.get(commit) ?: return false
    if (path.deleted) {
      if (!changes.containsValue(ChangeKind.REMOVED)) return false
      if (!verify) return true
      for (parent in changes.keys()) {
        if (commitToRename.get(UnorderedPair(commit, parent)).firstNotNull { rename ->
            rename.getOtherPath(parent, path.filePath)
          } != null) {
          // this is a rename from path to something else, we should not match this commit
          return false
        }
      }
      return true
    }
    return !changes.containsValue(ChangeKind.REMOVED)
  }

  fun getCommits(): Set<Int> {
    val result = mutableSetOf<Int>()
    affectedCommits.forEach { _, commit, _ ->
      result.add(commit)
    }
    return result
  }

  fun buildPathsMap(): Map<Int, MaybeDeletedFilePath> {
    val result = mutableMapOf<Int, MaybeDeletedFilePath>()
    affectedCommits.forEach { filePath, commit, changes ->
      result[commit] = MaybeDeletedFilePath(filePath, changes.containsValue(ChangeKind.REMOVED))
    }
    return result
  }

  fun getChanges(filePath: FilePath, commit: Int) = affectedCommits[filePath]?.get(commit)

  fun forEach(action: (FilePath, Int, TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>) -> Unit) = affectedCommits.forEach(action)

  fun removeAll(commits: List<Int>) {
    affectedCommits.forEach { _, commitsMap -> commitsMap.removeAll(commits) }
  }

  abstract fun findRename(parent: Int, child: Int, accept: (Couple<FilePath>) -> Boolean): Couple<FilePath>?
  abstract fun getAffectedCommits(path: FilePath): TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>>
}

private class AdditionDeletion(val filePath: FilePath, val child: Int, val parent: Int, val isAddition: Boolean) {
  val commits
    get() = UnorderedPair(parent, child)

  fun matches(rename: Rename): Boolean {
    if (rename.commit1 == parent && rename.commit2 == child) {
      return if (isAddition) FILE_PATH_HASHING_STRATEGY.equals(rename.filePath2, filePath)
      else FILE_PATH_HASHING_STRATEGY.equals(rename.filePath1, filePath)
    }
    else if (rename.commit2 == parent && rename.commit1 == child) {
      return if (isAddition) FILE_PATH_HASHING_STRATEGY.equals(rename.filePath1, filePath)
      else FILE_PATH_HASHING_STRATEGY.equals(rename.filePath2, filePath)
    }
    return false
  }

  fun matches(files: Couple<FilePath>): Boolean {
    return (isAddition && FILE_PATH_HASHING_STRATEGY.equals(files.second, filePath)) ||
           (!isAddition && FILE_PATH_HASHING_STRATEGY.equals(files.first, filePath))
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as AdditionDeletion

    if (!FILE_PATH_HASHING_STRATEGY.equals(filePath, other.filePath)) return false
    if (child != other.child) return false
    if (parent != other.parent) return false
    if (isAddition != other.isAddition) return false

    return true
  }

  override fun hashCode(): Int {
    var result = FILE_PATH_HASHING_STRATEGY.computeHashCode(filePath)
    result = 31 * result + child
    result = 31 * result + parent
    result = 31 * result + isAddition.hashCode()
    return result
  }
}

private class Rename(val filePath1: FilePath, val filePath2: FilePath, val commit1: Int, val commit2: Int) {

  fun getOtherPath(commit: Int, filePath: FilePath): FilePath? {
    if (commit == commit1 && FILE_PATH_HASHING_STRATEGY.equals(filePath, filePath1)) return filePath2
    if (commit == commit2 && FILE_PATH_HASHING_STRATEGY.equals(filePath, filePath2)) return filePath1
    return null
  }

  fun getOtherPath(ad: AdditionDeletion): FilePath? {
    return getOtherPath(if (ad.isAddition) ad.child else ad.parent, ad.filePath)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Rename

    if (!FILE_PATH_HASHING_STRATEGY.equals(filePath1, other.filePath1)) return false
    if (!FILE_PATH_HASHING_STRATEGY.equals(filePath2, other.filePath2)) return false
    if (commit1 != other.commit1) return false
    if (commit2 != other.commit2) return false

    return true
  }

  override fun hashCode(): Int {
    var result = FILE_PATH_HASHING_STRATEGY.computeHashCode(filePath1)
    result = 31 * result + FILE_PATH_HASHING_STRATEGY.computeHashCode(filePath2)
    result = 31 * result + commit1
    result = 31 * result + commit2
    return result
  }
}

class MaybeDeletedFilePath(val filePath: FilePath, val deleted: Boolean) {
  constructor(filePath: FilePath) : this(filePath, false)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MaybeDeletedFilePath

    if (!FILE_PATH_HASHING_STRATEGY.equals(filePath, other.filePath)) return false
    if (deleted != other.deleted) return false

    return true
  }

  override fun hashCode(): Int {
    var result = FILE_PATH_HASHING_STRATEGY.computeHashCode(filePath)
    result = 31 * result + deleted.hashCode()
    return result
  }
}

internal fun Map<FilePath, TIntObjectHashMap<TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>>>.forEach(action: (FilePath, Int, TIntObjectHashMap<VcsLogPathsIndex.ChangeKind>) -> Unit) {
  forEach { (filePath, affectedCommits) ->
    affectedCommits.forEachEntry { commit, changesMap ->
      action(filePath, commit, changesMap)
      true
    }
  }
}

internal fun TIntObjectHashMap<*>.removeAll(keys: List<Int>) {
  keys.forEach { this.remove(it) }
}

internal fun TIntHashSet.removeAll(elements: TIntHashSet): Boolean {
  var result = false
  for (i in elements) {
    result = this.remove(i) or result
  }
  return result
}

private fun <E, R> Collection<E>.firstNotNull(mapping: (E) -> R): R? {
  for (e in this) {
    val value = mapping(e)
    if (value != null) return value
  }
  return null
}

@JvmField
val FILE_PATH_HASHING_STRATEGY: TObjectHashingStrategy<FilePath> = FilePathCaseSensitiveStrategy()

internal class FilePathCaseSensitiveStrategy : TObjectHashingStrategy<FilePath> {
  override fun equals(path1: FilePath?, path2: FilePath?): Boolean {
    if (path1 === path2) return true
    if (path1 == null || path2 == null) return false

    if (path1.isDirectory != path2.isDirectory) return false
    val canonical1 = FileUtil.toCanonicalPath(path1.path)
    val canonical2 = FileUtil.toCanonicalPath(path2.path)
    return canonical1 == canonical2
  }

  override fun computeHashCode(path: FilePath?): Int {
    if (path == null) return 0

    var result = if (path.path.isEmpty()) 0 else FileUtil.toCanonicalPath(path.path).hashCode()
    result = 31 * result + if (path.isDirectory) 1 else 0
    return result
  }
}