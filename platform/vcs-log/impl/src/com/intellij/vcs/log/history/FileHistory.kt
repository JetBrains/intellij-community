// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UnorderedPair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.containers.MultiMap
import com.intellij.vcs.log.data.index.ChangeKind
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph
import com.intellij.vcs.log.graph.impl.facade.*
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.isAncestor
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus
import java.util.function.BiConsumer

class FileHistory internal constructor(internal val commitToFileStateMap: Map<Int, CommitFileState>,
                                       internal val processedAdditionsDeletions: Set<AdditionDeletion> = emptySet(),
                                       internal val unmatchedAdditionsDeletions: Set<AdditionDeletion> = emptySet(),
                                       internal val commitToRename: MultiMap<UnorderedPair<Int>, Rename> = MultiMap.empty()) {
  companion object {
    internal val EMPTY = FileHistory(emptyMap())
  }
}

internal class FileHistoryBuilder(private val startCommit: Int?,
                                  private val startPath: FilePath,
                                  private val fileHistoryData: FileHistoryData,
                                  private val oldFileHistory: FileHistory,
                                  private val commitsToHide: Set<Int> = emptySet(),
                                  private val removeTrivialMerges: Boolean = true,
                                  private val refine: Boolean = true) : BiConsumer<LinearGraphController, PermanentGraphInfo<Int>> {
  private val fileStateMap = mutableMapOf<Int, CommitFileState>()
  private val processedAdditionsDeletions = mutableSetOf<AdditionDeletion>()
  private val unmatchedAdditionsDeletions = mutableSetOf<AdditionDeletion>()
  private val commitToRename = MultiMap<UnorderedPair<Int>, Rename>()

  val fileHistory: FileHistory
    get() = FileHistory(fileStateMap, processedAdditionsDeletions, unmatchedAdditionsDeletions, commitToRename)

  override fun accept(controller: LinearGraphController, permanentGraphInfo: PermanentGraphInfo<Int>) {
    val needToRepeat = removeTrivialMerges &&
                       removeTrivialMerges(controller, permanentGraphInfo, fileHistoryData, this::reportTrivialMerges)

    fileStateMap.putAll(refine(controller, startCommit, permanentGraphInfo))

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
                     permanentGraphInfo: PermanentGraphInfo<Int>): Map<Int, CommitFileState> {
    val visibleLinearGraph = controller.compiledGraph
    if (visibleLinearGraph.nodesCount() > 0 && fileHistoryData.hasRenames && refine) {

      val (row, fileState) = startCommit?.let {
        findAncestorRowAffectingFile(startCommit, visibleLinearGraph, permanentGraphInfo)
      } ?: Pair(0, CommitFileState(startPath))
      if (row >= 0) {
        val refiner = FileHistoryRefiner(visibleLinearGraph, permanentGraphInfo, fileHistoryData)
        val (fileStates, excluded) = refiner.refine(row, fileState)
        if (excluded.isNotEmpty()) {
          LOG.info("Excluding ${excluded.size} commits from history for ${startPath.path}")
          val hidden = hideCommits(controller, permanentGraphInfo, excluded)
          if (!hidden) LOG.error("Could not hide excluded commits from history for ${startPath.path}")
        }
        return fileStates
      }
    }
    return fileHistoryData.buildFileStatesMap()
  }

  private fun findAncestorRowAffectingFile(commitId: Int,
                                           visibleLinearGraph: LinearGraph,
                                           permanentGraphInfo: PermanentGraphInfo<Int>): Pair<Int, CommitFileState> {
    val existing = CommitFileState(startPath)
    val deleted = CommitFileState(startPath, true)
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

    @JvmField
    internal val removeTrivialMergesValue = Registry.get("vcs.history.remove.trivial.merges")
    @JvmField
    internal val refineValue = Registry.get("vcs.history.refine")

    internal val isRemoveTrivialMerges get() = removeTrivialMergesValue.asBoolean()
    internal val isRefine get() = refineValue.asBoolean()
  }
}

@ApiStatus.Internal
fun removeTrivialMerges(controller: LinearGraphController,
                        permanentGraphInfo: PermanentGraphInfo<Int>,
                        fileHistoryData: FileHistoryData,
                        report: (Set<Int>) -> Unit): Boolean {
  val trivialCandidates = IntOpenHashSet()
  val nonTrivialMerges = IntOpenHashSet()
  fileHistoryData.forEach { _, commit, changes ->
    if (changes.size > 1) {
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

  if (!trivialCandidates.isEmpty()) {
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

@ApiStatus.Internal
abstract class FileHistoryData(internal val startPaths: Collection<FilePath>) {
  // file -> (commitId -> (parent commitId -> change kind))
  private val affectedCommits = CollectionFactory.createCustomHashingStrategyMap<FilePath, Int2ObjectMap<Int2ObjectMap<ChangeKind>>>(FILE_PATH_HASHING_STRATEGY)
  internal val commitToRename = MultiMap<UnorderedPair<Int>, Rename>()

  val isEmpty: Boolean
    get() = affectedCommits.isEmpty()
  val hasRenames: Boolean
    get() = !commitToRename.isEmpty
  val files: Set<FilePath>
    get() = affectedCommits.keys

  constructor(startPath: FilePath) : this(listOf(startPath))

  internal fun build(oldRenames: MultiMap<UnorderedPair<Int>, Rename>): FileHistoryData {
    val newPaths = CollectionFactory.createCustomHashingStrategySet(FILE_PATH_HASHING_STRATEGY)
    newPaths.addAll(startPaths)

    while (newPaths.isNotEmpty()) {
      val commits = CollectionFactory.createCustomHashingStrategyMap<FilePath, Int2ObjectMap<Int2ObjectMap<ChangeKind>>>(FILE_PATH_HASHING_STRATEGY)
      newPaths.associateWithTo(commits) { getAffectedCommits(it) }
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

  private fun iterateUnmatchedAdditionsDeletions(commits: Map<FilePath, Int2ObjectMap<Int2ObjectMap<ChangeKind>>>,
                                                 action: (AdditionDeletion) -> Unit) {
    forEach(commits) { path, commit, changes ->
      changes.int2ObjectEntrySet().forEach { entry ->
        val parent = entry.intKey
        val change = entry.value
        if (parent != commit && (change == ChangeKind.ADDED || change == ChangeKind.REMOVED)) {
          val ad = AdditionDeletion(path, commit, parent, change == ChangeKind.ADDED)
          if (!commitToRename[ad.commits].any { rename -> ad.matches(rename) }) {
            action(ad)
          }
        }
      }
      ProgressManager.checkCanceled()
    }
  }

  internal fun iterateUnmatchedAdditionsDeletions(action: (AdditionDeletion) -> Unit) {
    iterateUnmatchedAdditionsDeletions(affectedCommits) {
      action(it)
    }
  }

  fun getFileStateInParentRevision(commit: Int, parent: Int, childState: CommitFileState): CommitFileState {
    val childFilePath = childState.filePath
    val changeKind = affectedCommits[childFilePath]?.get(commit)?.get(parent) ?: return childState
    if (changeKind == ChangeKind.NOT_CHANGED) return childState

    val renames = commitToRename.get(UnorderedPair(commit, parent))
    if (!childState.deleted) {
      val otherPath = renames.firstNotNull { rename -> rename.getOtherPath(commit, childFilePath) }
      if (otherPath != null) return CommitFileState(otherPath)
      return CommitFileState(childFilePath, changeKind == ChangeKind.ADDED)
    }

    if (changeKind == ChangeKind.REMOVED) {
      // checking if this is actually an unrelated rename
      if (renames.firstNotNull { rename -> rename.getOtherPath(parent, childFilePath) } != null) return childState
    }
    return CommitFileState(childFilePath, changeKind != ChangeKind.REMOVED)
  }

  fun getFileStateInChildRevision(commit: Int, parent: Int, parentState: CommitFileState): CommitFileState {
    val parentFilePath = parentState.filePath
    val changeKind = affectedCommits[parentFilePath]?.get(commit)?.get(parent) ?: return parentState
    if (changeKind == ChangeKind.NOT_CHANGED) return parentState

    val renames = commitToRename.get(UnorderedPair(commit, parent))
    if (!parentState.deleted) {
      val otherPath = renames.firstNotNull { rename -> rename.getOtherPath(parent, parentFilePath) }
      if (otherPath != null) return CommitFileState(otherPath)
      return CommitFileState(parentFilePath, changeKind == ChangeKind.REMOVED)
    }

    if (changeKind == ChangeKind.ADDED) {
      // checking if this is actually an unrelated rename
      if (renames.firstNotNull { rename -> rename.getOtherPath(commit, parentFilePath) } != null) return parentState
    }
    return CommitFileState(parentFilePath, changeKind != ChangeKind.ADDED)
  }

  fun affects(commit: Int, path: CommitFileState, verify: Boolean = false): Boolean {
    val changes = affectedCommits[path.filePath]?.get(commit) ?: return false
    if (path.deleted) {
      if (!changes.containsValue(ChangeKind.REMOVED)) return false
      if (!verify) return true
      for (entry in changes.int2ObjectEntrySet()) {
        val parent = entry.intKey
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

  fun getCommits(): IntSet {
    val result = IntOpenHashSet()
    forEach(affectedCommits) { _, commit, _ ->
      result.add(commit)
    }
    return result
  }

  fun getCommitsWithRenames(): Set<Int> {
    return commitToRename.values().mapTo(mutableSetOf()) { rename -> rename.childCommit }
  }

  fun buildFileStatesMap(): Map<Int, CommitFileState> {
    val result = mutableMapOf<Int, CommitFileState>()
    forEach(affectedCommits) { filePath, commit, changes ->
      result[commit] = CommitFileState(filePath, changes.containsValue(ChangeKind.REMOVED))
    }
    return result
  }

  fun forEach(action: (FilePath, Int, Int2ObjectMap<ChangeKind>) -> Unit) = forEach(affectedCommits, action)

  fun removeAll(commits: List<Int>) {
    affectedCommits.forEach { (_, commitsMap) -> commitsMap.removeAll(commits) }
  }

  abstract fun findRename(parent: Int, child: Int, path: FilePath, isChildPath: Boolean): EdgeData<FilePath>?
  abstract fun getAffectedCommits(path: FilePath): Int2ObjectMap<Int2ObjectMap<ChangeKind>>
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
    return isAddition == other.isAddition
  }

  override fun hashCode(): Int {
    var result = FILE_PATH_HASHING_STRATEGY.hashCode(filePath)
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
    return childCommit == other.childCommit
  }

  override fun hashCode(): Int {
    var result = FILE_PATH_HASHING_STRATEGY.hashCode(parentPath)
    result = 31 * result + FILE_PATH_HASHING_STRATEGY.hashCode(childPath)
    result = 31 * result + parentCommit
    result = 31 * result + childCommit
    return result
  }
}

@ApiStatus.Internal
class CommitFileState(val filePath: FilePath, val deleted: Boolean) {
  constructor(filePath: FilePath) : this(filePath, false)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CommitFileState

    if (!FILE_PATH_HASHING_STRATEGY.equals(filePath, other.filePath)) return false
    return deleted == other.deleted
  }

  override fun hashCode(): Int {
    var result = FILE_PATH_HASHING_STRATEGY.hashCode(filePath)
    result = 31 * result + deleted.hashCode()
    return result
  }

  override fun toString(): String {
    return "MaybeDeletedFilePath(filePath=$filePath, deleted=$deleted)"
  }
}

internal fun forEach(map: Map<FilePath, Int2ObjectMap<Int2ObjectMap<ChangeKind>>>,
                     action: (FilePath, Int, Int2ObjectMap<ChangeKind>) -> Unit) {
  for ((filePath, affectedCommits) in map) {
    affectedCommits.int2ObjectEntrySet().forEach { entry ->
      val commit = entry.intKey
      val changesMap = entry.value
      action(filePath, commit, changesMap)
    }
  }
}

internal fun Int2ObjectMap<*>.removeAll(keys: List<Int>) {
  keys.forEach(this::remove)
}

private fun <E, R> Collection<E>.firstNotNull(mapping: (E) -> R): R? {
  for (e in this) {
    val value = mapping(e)
    if (value != null) return value
  }
  return null
}

@JvmField
internal val FILE_PATH_HASHING_STRATEGY: HashingStrategy<FilePath> = ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY

@ApiStatus.Internal
data class EdgeData<T>(@JvmField val parent: T, @JvmField val child: T)
