// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.util.ObjectUtils.notNull
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.Stack
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.BfsUtil
import com.intellij.vcs.log.graph.utils.DfsUtil
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags

internal class FileHistoryRefiner(private val myVisibleGraph: VisibleGraphImpl<Int>,
                                  private val myNamesData: IndexDataGetter.FileNamesData) : DfsUtil.NodeVisitor {
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
