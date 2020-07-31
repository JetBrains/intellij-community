// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.util.containers.IntStack
import com.intellij.util.containers.Stack
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.utils.Dfs
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.getCorrespondingParent
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import gnu.trove.THashSet
import java.util.*

internal class FileHistoryRefiner(private val visibleLinearGraph: LinearGraph,
                                  permanentGraphInfo: PermanentGraphInfo<Int>,
                                  private val historyData: FileHistoryData) {
  private val permanentCommitsInfo: PermanentCommitsInfo<Int> = permanentGraphInfo.permanentCommitsInfo
  private val permanentLinearGraph: LiteLinearGraph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)

  private val paths = Stack<MaybeDeletedFilePath>()
  private val visibilityBuffer = BitSetFlags(permanentLinearGraph.nodesCount()) // a reusable buffer for bfs
  private val pathsForCommits = HashMap<Int, MaybeDeletedFilePath>()

  fun refine(row: Int, startPath: MaybeDeletedFilePath): Pair<Map<Int, MaybeDeletedFilePath>, Set<Int>> {
    paths.push(startPath)
    walk(LinearGraphUtils.asLiteLinearGraph(visibleLinearGraph), row)

    val excluded = THashSet<Int>()
    for ((commit, path) in pathsForCommits) {
      if (!historyData.affects(commit, path, true)) {
        excluded.add(commit)
      }
    }

    excluded.forEach { pathsForCommits.remove(it) }
    return Pair(pathsForCommits, excluded)
  }

  /*
   * Depth-first walk for a graph. For each node, walks both into upward and downward siblings.
   * Tries to preserve direction of travel: when a node is entered from up-sibling, goes to the down-siblings first.
   * Then goes to the other up-siblings.
   * And when a node is entered from down-sibling, goes to the up-siblings first.
   * Then goes to the other down-siblings.
   * When a node is entered the first time, enterNode is called.
   * When a all the siblings of the node are visited, exitNode is called.
   */
  private fun walk(graph: LiteLinearGraph, start: Int) {
    if (start < 0 || start >= graph.nodesCount()) return

    val visited = BitSetFlags(graph.nodesCount(), false)

    val stack = IntStack()
    stack.push(start) // commit + direction of travel

    outer@ while (!stack.empty()) {
      val currentNode = stack.peek()
      val down = isDown(stack)
      if (!visited.get(currentNode)) {
        visited.set(currentNode, true)
        enterNode(currentNode, getPreviousNode(stack), down)
      }

      for (nextNode in graph.getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)) {
        if (!visited.get(nextNode)) {
          stack.push(nextNode)
          continue@outer
        }
      }

      for (nextNode in graph.getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.UP else LiteLinearGraph.NodeFilter.DOWN)) {
        if (!visited.get(nextNode)) {
          stack.push(nextNode)
          continue@outer
        }
      }

      exitNode(currentNode)
      stack.pop()
    }
  }

  private fun enterNode(currentNode: Int, previousNode: Int, down: Boolean) {
    val currentPath = getPath(currentNode, previousNode, paths.last(), down)
    val currentCommitId = permanentCommitsInfo.getCommitId(visibleLinearGraph.getNodeId(currentNode))

    pathsForCommits[currentCommitId] = currentPath
    paths.push(currentPath)
  }

  private fun getPath(currentNode: Int, previousNode: Int, previousPath: MaybeDeletedFilePath, down: Boolean): MaybeDeletedFilePath {
    if (previousNode == Dfs.NextNode.NODE_NOT_FOUND) return previousPath

    val previousNodeId = visibleLinearGraph.getNodeId(previousNode)
    val currentNodeId = visibleLinearGraph.getNodeId(currentNode)

    if (down) {
      val previousCommit = permanentCommitsInfo.getCommitId(previousNodeId)
      val pathGetter = { parentIndex: Int ->
        historyData.getPathInParentRevision(previousCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
      }
      val path = findPathWithoutConflict(previousNodeId, pathGetter)
      return path ?: pathGetter(permanentLinearGraph.getCorrespondingParent(previousNodeId, currentNodeId, visibilityBuffer))
    }
    else {
      val currentCommit = permanentCommitsInfo.getCommitId(currentNodeId)
      val pathGetter = { parentIndex: Int ->
        historyData.getPathInChildRevision(currentCommit, permanentCommitsInfo.getCommitId(parentIndex), previousPath)
      }
      val path = findPathWithoutConflict(currentNodeId, pathGetter)
      return path ?: pathGetter(permanentLinearGraph.getCorrespondingParent(currentNodeId, previousNodeId, visibilityBuffer))
    }
  }

  private fun findPathWithoutConflict(nodeId: Int, pathGetter: (Int) -> MaybeDeletedFilePath): MaybeDeletedFilePath? {
    val parents = permanentLinearGraph.getNodes(nodeId, LiteLinearGraph.NodeFilter.DOWN)
    val path = pathGetter(parents.first())
    if (parents.size == 1) return path

    if (parents.subList(1, parents.size).find { pathGetter(it) != path } != null) return null
    return path
  }

  private fun exitNode(node: Int) {
    paths.pop()
  }
}

private fun getPreviousNode(stack: IntStack): Int {
  return if (stack.size() < 2) {
    Dfs.NextNode.NODE_NOT_FOUND
  }
  else stack.get(stack.size() - 2)
}

private fun isDown(stack: IntStack): Boolean {
  val currentNode = stack.peek()
  val previousNode = getPreviousNode(stack)
  if (previousNode == Dfs.NextNode.NODE_NOT_FOUND) return true
  return previousNode < currentNode
}