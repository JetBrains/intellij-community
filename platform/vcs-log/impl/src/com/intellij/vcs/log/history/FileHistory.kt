// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UnorderedPair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.Stack
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

class FileHistory internal constructor(val commitsToPathsMap: Map<Int, MaybeDeletedFilePath>,
                                       internal val processedAdditionsDeletions: Set<AdditionDeletion> = emptySet(),
                                       internal val unmatchedAdditionsDeletions: Set<AdditionDeletion> = emptySet(),
                                       internal val commitToRename: MultiMap<UnorderedPair<Int>, Rename> = MultiMap.empty())

internal val EMPTY_HISTORY = FileHistory(emptyMap())

internal class FileHistoryBuilder(private val startCommit: Int?,
                                  private val startPath: FilePath,
                                  private val fileHistoryData: FileHistoryData,
                                  private val oldFileHistory: FileHistory,
                                  private val commitsToHide: Set<Int> = emptySet()) : BiConsumer<LinearGraphController, PermanentGraphInfo<Int>> {
  private val pathsMap = mutableMapOf<Int, MaybeDeletedFilePath>()
  private val processedAdditionsDeletions = mutableSetOf<AdditionDeletion>()
  private val unmatchedAdditionsDeletions = mutableSetOf<AdditionDeletion>()
  private val commitToRename = MultiMap.createSmart<UnorderedPair<Int>, Rename>()

  val fileHistory: FileHistory
    get() = FileHistory(pathsMap, processedAdditionsDeletions, unmatchedAdditionsDeletions, commitToRename)

  override fun accept(controller: LinearGraphController, permanentGraphInfo: PermanentGraphInfo<Int>) {
    val needToRepeat = removeTrivialMerges(controller, permanentGraphInfo, fileHistoryData, this::reportTrivialMerges)

    pathsMap.putAll(refine(controller, startCommit, permanentGraphInfo))

    if (needToRepeat) {
      LOG.info("Some merge commits were not excluded from file history for ${startPath.path}")
      removeTrivialMerges(controller, permanentGraphInfo, fileHistoryData, this::reportTrivialMerges)
    }

    collectAdditionsDeletions(controller, permanentGraphInfo)
    commitToRename.putAllValues(fileHistoryData.commitToRename)

    if (commitsToHide.isNotEmpty()) hideCommits(controller, permanentGraphInfo, commitsToHide)
  }

  private fun collectAdditionsDeletions(controller: LinearGraphController, permanentGraphInfo: PermanentGraphInfo<Int>) {
    processedAdditionsDeletions.addAll(oldFileHistory.processedAdditionsDeletions)
    processedAdditionsDeletions.addAll(oldFileHistory.unmatchedAdditionsDeletions)

    val additionsDeletions = mutableSetOf<AdditionDeletion>()
    fileHistoryData.iterateUnmatchedAdditionsDeletions { ad ->
      if (!processedAdditionsDeletions.contains(ad)) {
        additionsDeletions.add(ad)
      }
    }
    if (additionsDeletions.isNotEmpty()) {
      val grouped = additionsDeletions.groupBy { it.child }
      for (row in 0 until controller.compiledGraph.nodesCount()) {
        val commitId = permanentGraphInfo.permanentCommitsInfo.getCommitId(controller.compiledGraph.getNodeId(row))
        grouped[commitId]?.let { unmatchedAdditionsDeletions.addAll(it) }
      }
    }
  }

  private fun reportTrivialMerges(trivialMerges: Set<Int>) {
    LOG.debug("Excluding ${trivialMerges.size} trivial merges from history for ${startPath.path}")
  }

  private fun refine(controller: LinearGraphController,
                     startCommit: Int?,
                     permanentGraphInfo: PermanentGraphInfo<Int>): Map<Int, MaybeDeletedFilePath> {
    if (fileHistoryData.hasRenames && Registry.`is`("vcs.history.refine")) {
      val visibleLinearGraph = controller.compiledGraph

      val (row, path) = startCommit?.let {
        findAncestorRowAffectingFile(startCommit, visibleLinearGraph, permanentGraphInfo)
      } ?: Pair(0, MaybeDeletedFilePath(startPath))
      if (row >= 0) {
        val refiner = FileHistoryRefiner(visibleLinearGraph, permanentGraphInfo, fileHistoryData)
        val (paths, excluded) = refiner.refine(row, path)
        if (excluded.isNotEmpty()) {
          LOG.info("Excluding ${excluded.size} commits from history for ${startPath.path}")
          val hidden = hideCommits(controller, permanentGraphInfo, excluded)
          if (!hidden) LOG.error("Could not hide excluded commits from history for ${startPath.path}")
        }
        return paths
      }
    }
    return fileHistoryData.buildPathsMap()
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
        fileHistoryData.affects(id, existing) -> true
        fileHistoryData.affects(id, deleted) -> {
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
                        fileHistoryData: FileHistoryData,
                        report: (Set<Int>) -> Unit): Boolean {
  val trivialCandidates = TIntHashSet()
  val nonTrivialMerges = TIntHashSet()
  fileHistoryData.forEach { _, commit, changes ->
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
      fileHistoryData.removeAll(trivialMerges.map { permanentGraphInfo.permanentCommitsInfo.getCommitId(it) })
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
                                  private val historyData: FileHistoryData) : Dfs.NodeVisitor {
  private val permanentCommitsInfo: PermanentCommitsInfo<Int> = permanentGraphInfo.permanentCommitsInfo
  private val permanentLinearGraph: LiteLinearGraph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)

  private val paths = Stack<MaybeDeletedFilePath>()
  private val visibilityBuffer = BitSetFlags(permanentLinearGraph.nodesCount()) // a reusable buffer for bfs
  private val pathsForCommits = HashMap<Int, MaybeDeletedFilePath>()

  fun refine(row: Int, startPath: MaybeDeletedFilePath): Pair<Map<Int, MaybeDeletedFilePath>, Set<Int>> {
    paths.push(startPath)
    LinearGraphUtils.asLiteLinearGraph(visibleLinearGraph).walk(row, this)

    val excluded = THashSet<Int>()
    for ((commit, path) in pathsForCommits) {
      if (!historyData.affects(commit, path, true)) {
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
          historyData.getPathInParentRevision(previousCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
        }
        val path = findPathWithoutConflict(previousNodeId, pathGetter)
        path ?: pathGetter(permanentLinearGraph.getCorrespondingParent(previousNodeId, currentNodeId, visibilityBuffer))
      }
      else {
        val pathGetter = { parentIndex: Int ->
          historyData.getPathInChildRevision(currentCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
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

abstract class FileHistoryData(internal val startPaths: Collection<FilePath>) {
  // file -> (commitId -> (parent commitId -> change kind))
  private val affectedCommits = THashMap<FilePath, TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>>(FILE_PATH_HASHING_STRATEGY)
  internal val commitToRename = MultiMap.createSmart<UnorderedPair<Int>, Rename>()

  val isEmpty: Boolean
    get() = affectedCommits.isEmpty
  val hasRenames: Boolean
    get() = !commitToRename.isEmpty
  val files: Set<FilePath>
    get() = affectedCommits.keys

  constructor(startPath: FilePath) : this(listOf(startPath))

  internal fun build(oldRenames: MultiMap<UnorderedPair<Int>, Rename>): FileHistoryData {
    val newPaths = THashSet<FilePath>(FILE_PATH_HASHING_STRATEGY)
    newPaths.addAll(startPaths)

    while (newPaths.isNotEmpty()) {
      val commits = THashMap<FilePath, TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>>(FILE_PATH_HASHING_STRATEGY)
      newPaths.associateTo(commits) { Pair(it, getAffectedCommits(it)) }
      affectedCommits.putAll(commits)
      newPaths.clear()

      iterateUnmatchedAdditionsDeletions(commits) { ad ->
        val rename = oldRenames.get(ad.commits).find { ad.matches(it) } ?: findRename(ad)
        if (rename != null) {
          commitToRename.putValue(ad.commits, rename)
          val otherPath = rename.getOtherPath(ad)!!
          if (!affectedCommits.containsKey(otherPath)) {
            newPaths.add(otherPath)
          }
        }
      }
    }
    return this
  }

  private fun findRename(ad: AdditionDeletion): Rename? {
    return findRename(ad.parent, ad.child, ad.filePath, ad.isAddition)?.let { files ->
      Rename(files.parent, files.child, ad.parent, ad.child)
    }
  }

  fun build(): FileHistoryData = build(MultiMap.empty())

  private fun iterateUnmatchedAdditionsDeletions(commits: Map<FilePath, TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>>,
                                                 action: (AdditionDeletion) -> Unit) {
    commits.forEach { path, commit, changes ->
      changes.forEachEntry { parent, change ->
        if (parent != commit && (change == ChangeKind.ADDED || change == ChangeKind.REMOVED)) {
          val ad = AdditionDeletion(path, commit, parent, change == ChangeKind.ADDED)
          if (!commitToRename[ad.commits].any { rename -> ad.matches(rename) }) {
            action(ad)
          }
        }
        true
      }
    }
  }

  internal fun iterateUnmatchedAdditionsDeletions(action: (AdditionDeletion) -> Unit) {
    iterateUnmatchedAdditionsDeletions(affectedCommits) {
      action(it)
    }
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

  fun getCommitsWithRenames(): Set<Int> {
    return commitToRename.values().mapTo(mutableSetOf()) { rename -> rename.childCommit }
  }

  fun buildPathsMap(): Map<Int, MaybeDeletedFilePath> {
    val result = mutableMapOf<Int, MaybeDeletedFilePath>()
    affectedCommits.forEach { filePath, commit, changes ->
      result[commit] = MaybeDeletedFilePath(filePath, changes.containsValue(ChangeKind.REMOVED))
    }
    return result
  }

  fun forEach(action: (FilePath, Int, TIntObjectHashMap<ChangeKind>) -> Unit) = affectedCommits.forEach(action)

  fun removeAll(commits: List<Int>) {
    affectedCommits.forEach { _, commitsMap -> commitsMap.removeAll(commits) }
  }

  abstract fun findRename(parent: Int, child: Int, path: FilePath, isChildPath: Boolean): EdgeData<FilePath>?
  abstract fun getAffectedCommits(path: FilePath): TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>
}

internal class AdditionDeletion(val filePath: FilePath, val child: Int, val parent: Int, val isAddition: Boolean) {
  val commits
    get() = UnorderedPair(parent, child)

  fun matches(rename: Rename): Boolean {
    if (rename.parentCommit == parent && rename.childCommit == child) {
      return if (isAddition) FILE_PATH_HASHING_STRATEGY.equals(rename.childPath, filePath)
      else FILE_PATH_HASHING_STRATEGY.equals(rename.parentPath, filePath)
    }
    else if (rename.childCommit == parent && rename.parentCommit == child) {
      return if (isAddition) FILE_PATH_HASHING_STRATEGY.equals(rename.parentPath, filePath)
      else FILE_PATH_HASHING_STRATEGY.equals(rename.childPath, filePath)
    }
    return false
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

internal class Rename(val parentPath: FilePath, val childPath: FilePath, val parentCommit: Int, val childCommit: Int) {
  val commits
    get() = UnorderedPair(parentCommit, childCommit)

  fun getOtherPath(commit: Int, filePath: FilePath): FilePath? {
    if (commit == parentCommit && FILE_PATH_HASHING_STRATEGY.equals(filePath, parentPath)) return childPath
    if (commit == childCommit && FILE_PATH_HASHING_STRATEGY.equals(filePath, childPath)) return parentPath
    return null
  }

  fun getOtherPath(ad: AdditionDeletion): FilePath? {
    return getOtherPath(if (ad.isAddition) ad.child else ad.parent, ad.filePath)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Rename

    if (!FILE_PATH_HASHING_STRATEGY.equals(parentPath, other.parentPath)) return false
    if (!FILE_PATH_HASHING_STRATEGY.equals(childPath, other.childPath)) return false
    if (parentCommit != other.parentCommit) return false
    if (childCommit != other.childCommit) return false

    return true
  }

  override fun hashCode(): Int {
    var result = FILE_PATH_HASHING_STRATEGY.computeHashCode(parentPath)
    result = 31 * result + FILE_PATH_HASHING_STRATEGY.computeHashCode(childPath)
    result = 31 * result + parentCommit
    result = 31 * result + childCommit
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

internal fun Map<FilePath, TIntObjectHashMap<TIntObjectHashMap<ChangeKind>>>.forEach(action: (FilePath, Int, TIntObjectHashMap<ChangeKind>) -> Unit) {
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

data class EdgeData<T>(val parent: T, val child: T)