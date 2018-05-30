// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.ObjectUtils.notNull
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.intellij.vcs.log.data.index.VcsLogPathsIndex
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.BfsUtil
import com.intellij.vcs.log.graph.utils.DfsUtil
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import gnu.trove.TIntObjectHashMap
import gnu.trove.TIntProcedure

internal class FileHistoryRefiner(private val myVisibleGraph: VisibleGraphImpl<Int>,
                                  private val myNamesData: FileNamesData) : DfsUtil.NodeVisitor {
  private val permanentCommitsInfo: PermanentCommitsInfo<Int> = myVisibleGraph.permanentGraph.permanentCommitsInfo
  private val permanentLinearGraph: LiteLinearGraph = LinearGraphUtils.asLiteLinearGraph(myVisibleGraph.permanentGraph.linearGraph)

  private val paths = Stack<FilePath>()
  private val visibilityBuffer = BitSetFlags(permanentLinearGraph.nodesCount()) // a reusable buffer for bfs
  val pathsForCommits = ContainerUtil.newHashMap<Int, FilePath>()
  private val excluded = ContainerUtil.newHashSet<Int>()

  fun refine(row: Int, startPath: FilePath): Boolean {
    if (myNamesData.hasRenames()) {
      paths.push(startPath)
      DfsUtil.walk(LinearGraphUtils.asLiteLinearGraph(myVisibleGraph.linearGraph), row, this)
    }
    else {
      pathsForCommits.putAll(myNamesData.buildPathsMap())
    }

    for (commit in pathsForCommits.keys) {
      val path = pathsForCommits[commit]
      if (path != null) {
        if (!myNamesData.affects(commit, path)) excluded.add(commit)
        if (myNamesData.isTrivialMerge(commit, path)) excluded.add(commit)
      }
    }

    excluded.forEach { pathsForCommits.remove(it) }
    return !excluded.isEmpty()
  }

  override fun enterNode(currentNode: Int, previousNode: Int, down: Boolean) {
    val currentNodeId = myVisibleGraph.getNodeId(currentNode)
    val currentCommit = permanentCommitsInfo.getCommitId(currentNodeId)

    val previousPath = notNull(ContainerUtil.findLast(paths) { path -> path != null })
    var currentPath: FilePath? = previousPath

    if (previousNode != DfsUtil.NextNode.NODE_NOT_FOUND) {
      val previousNodeId = myVisibleGraph.getNodeId(previousNode)
      val previousCommit = permanentCommitsInfo.getCommitId(previousNodeId)

      if (down) {
        val pathGetter = { parentIndex: Int ->
          myNamesData.getPathInParentRevision(previousCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
        }
        currentPath = findPathWithoutConflict(previousNodeId, pathGetter)
        if (currentPath == null) {
          val parentIndex = BfsUtil.getCorrespondingParent(permanentLinearGraph, previousNodeId, currentNodeId, visibilityBuffer)
          currentPath = pathGetter(parentIndex)
        }
      }
      else {
        val pathGetter = { parentIndex: Int ->
          myNamesData.getPathInChildRevision(currentCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
        }
        currentPath = findPathWithoutConflict(currentNodeId, pathGetter)
        if (currentPath == null) {
          // since in reality there is no edge between the nodes, but the whole path, we need to know, which parent is affected by this path
          val parentIndex = BfsUtil.getCorrespondingParent(permanentLinearGraph, currentNodeId, previousNodeId, visibilityBuffer)
          currentPath = pathGetter(parentIndex)
        }
      }
    }

    pathsForCommits[currentCommit] = currentPath
    paths.push(currentPath)
  }

  private fun findPathWithoutConflict(nodeId: Int, pathGetter: (Int) -> FilePath?): FilePath? {
    val parents = permanentLinearGraph.getNodes(nodeId, LiteLinearGraph.NodeFilter.DOWN)
    val path = pathGetter(parents[0])
    if (parents.size == 1) return path

    for (parent in ContainerUtil.subList(parents, 1)) {
      if (pathGetter(parent) != path) {
        return null
      }
    }
    return path
  }

  override fun exitNode(node: Int) {
    paths.pop()
  }
}

abstract class FileNamesData {
  private val commitToPathAndChanges = TIntObjectHashMap<MutableMap<FilePath, MutableMap<Int, VcsLogPathsIndex.ChangeData?>>>()
  private var hasRenames = false

  val commits: Set<Int>
    get() {
      val result = ContainerUtil.newHashSet<Int>()
      commitToPathAndChanges.forEach(TIntProcedure { result.add(it) })
      return result
    }

  protected abstract fun getPathById(pathId: Int): FilePath

  fun hasRenames(): Boolean {
    return hasRenames
  }

  fun add(commit: Int,
          path: FilePath,
          changes: List<VcsLogPathsIndex.ChangeData?>,
          parents: List<Int>) {
    var pathToChanges: MutableMap<FilePath, MutableMap<Int, VcsLogPathsIndex.ChangeData?>>? = commitToPathAndChanges.get(commit)
    if (pathToChanges == null) {
      pathToChanges = ContainerUtil.newHashMap<FilePath, MutableMap<Int, VcsLogPathsIndex.ChangeData?>>()
      commitToPathAndChanges.put(commit, pathToChanges)
    }

    if (!hasRenames) {
      for (data in changes) {
        if (data == null) continue
        if (data.isRename) {
          hasRenames = true
          break
        }
      }
    }

    var parentToChangesMap: MutableMap<Int, VcsLogPathsIndex.ChangeData?>? = pathToChanges[path]
    if (parentToChangesMap == null) parentToChangesMap = ContainerUtil.newHashMap<Int, VcsLogPathsIndex.ChangeData?>()
    if (!parents.isEmpty()) {
      LOG.assertTrue(parents.size == changes.size)
      for (i in changes.indices) {
        val existing = parentToChangesMap[parents[i]]
        if (existing != null) {
          // since we occasionally reindex commits with different rename limit
          // it can happen that we have several change data for a file in a commit
          // one with rename, other without
          // we want to keep a renamed-one, so throwing the other one out
          if (existing.isRename) continue
        }
        parentToChangesMap[parents[i]] = changes[i]
      }
    }
    else {
      // initial commit
      LOG.assertTrue(changes.size == 1)
      parentToChangesMap[-1] = changes[0]
    }
    pathToChanges[path] = parentToChangesMap
  }

  fun getPathInParentRevision(commit: Int, parent: Int, childPath: FilePath): FilePath? {
    val filesToChangesMap = commitToPathAndChanges.get(commit)
    LOG.assertTrue(filesToChangesMap != null, "Missing commit $commit")
    val changes = filesToChangesMap!![childPath] ?: return childPath

    val change = changes[parent]
    if (change == null) {
      LOG.assertTrue(changes.size > 1)
      return childPath
    }
    if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_FROM) return null
    return if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_TO) {
      getPathById(change.otherPath)
    }
    else childPath
  }

  fun getPathInChildRevision(commit: Int, parentIndex: Int, parentPath: FilePath): FilePath? {
    val filesToChangesMap = commitToPathAndChanges.get(commit)
    LOG.assertTrue(filesToChangesMap != null, "Missing commit $commit")
    val changes = filesToChangesMap!![parentPath] ?: return parentPath

    val change = changes[parentIndex] ?: return parentPath
    if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_TO) return null
    return if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_FROM) {
      getPathById(change.otherPath)
    }
    else parentPath
  }

  fun affects(id: Int, path: FilePath): Boolean {
    return commitToPathAndChanges.containsKey(id) && commitToPathAndChanges.get(id).containsKey(path)
  }

  fun buildPathsMap(): Map<Int, FilePath> {
    val result = ContainerUtil.newHashMap<Int, FilePath>()

    commitToPathAndChanges.forEachEntry { commit, filesToChanges ->
      if (filesToChanges.size == 1) {
        result[commit] = ContainerUtil.getFirstItem(filesToChanges.keys)
      }
      else {
        for ((key, value) in filesToChanges) {
          val changeData = value.values.find { ch -> ch != null && ch.kind != VcsLogPathsIndex.ChangeKind.RENAMED_FROM }
          if (changeData != null) {
            result[commit] = key
            break
          }
        }
      }

      true
    }

    return result
  }

  fun isTrivialMerge(commit: Int, path: FilePath): Boolean {
    if (!commitToPathAndChanges.containsKey(commit)) return false
    val data = commitToPathAndChanges.get(commit)[path]
    // strictly speaking, the criteria for merge triviality is a little bit more tricky than this:
    // some merges have just reverted changes in one of the branches
    // they need to be displayed
    // but we skip them instead
    return data != null && data.size > 1 && data.containsValue(null)
  }

  companion object {
    private val LOG = Logger.getInstance(FileNamesData::class.java)
  }
}