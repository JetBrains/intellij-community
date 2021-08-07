// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.util.containers.Stack
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.utils.Dfs
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.getCorrespondingParent
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import java.util.*

internal class FileHistoryRefiner(private val visibleLinearGraph: LinearGraph,
                                  permanentGraphInfo: PermanentGraphInfo<Int>,
                                  private val historyData: FileHistoryData) {
  private val permanentCommitsInfo: PermanentCommitsInfo<Int> = permanentGraphInfo.permanentCommitsInfo
  private val permanentLinearGraph: LiteLinearGraph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)

  private val visibilityBuffer = BitSetFlags(permanentLinearGraph.nodesCount()) // a reusable buffer for bfs
  private val pathsForCommits = HashMap<Int, MaybeDeletedFilePath>()

  fun refine(row: Int, startPath: MaybeDeletedFilePath): Pair<Map<Int, MaybeDeletedFilePath>, Set<Int>> {
    walk(LinearGraphUtils.asLiteLinearGraph(visibleLinearGraph), row, startPath)

    val excluded = HashSet<Int>()
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
   */
  private fun walk(graph: LiteLinearGraph, startNode: Int, startPath: MaybeDeletedFilePath) {
    if (startNode < 0 || startNode >= graph.nodesCount()) {
      return
    }

    val visited = BitSetFlags(graph.nodesCount(), false)
    val starts: Queue<Pair<Int, MaybeDeletedFilePath>> = LinkedList<Pair<Int, MaybeDeletedFilePath>>().apply {
      add(Pair(startNode, startPath))
    }

    iterateStarts@ while (starts.isNotEmpty()) {
      val (nextStart, nextStartPath) = starts.poll()
      if (visited.get(nextStart)) continue@iterateStarts

      val stack = Stack(Pair(nextStart, nextStartPath))

      iterateStack@ while (!stack.empty()) {
        val (currentNode, currentPath) = stack.peek()
        val down = isDown(stack)
        if (!visited.get(currentNode)) {
          visited.set(currentNode, true)
          pathsForCommits[permanentCommitsInfo.getCommitId(visibleLinearGraph.getNodeId(currentNode))] = currentPath
        }

        for ((nextNode, nextPath) in getNextNodes(graph, visited, currentNode, currentPath, down)) {
          if (!currentPath.deleted && nextPath.deleted) {
            starts.add(Pair(nextNode, nextPath))
          } else {
            stack.push(Pair(nextNode, nextPath))
            continue@iterateStack
          }
        }

        for ((nextNode, nextPath) in getNextNodes(graph, visited, currentNode, currentPath, !down)) {
          if (!currentPath.deleted && nextPath.deleted) {
            starts.add(Pair(nextNode, nextPath))
          } else {
            stack.push(Pair(nextNode, nextPath))
            continue@iterateStack
          }
        }

        stack.pop()
      }
    }
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

  private fun getNextNodes(graph: LiteLinearGraph,
                           visited: BitSetFlags,
                           currentNode: Int,
                           currentPath: MaybeDeletedFilePath,
                           down: Boolean): List<Pair<Int, MaybeDeletedFilePath>> {
    val nextNodes = graph.getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)
    val nodesWithPaths = nextNodes.filterNot(visited::get).map { node -> Pair(node, getPath(node, currentNode, currentPath, down)) }

    return nodesWithPaths.sortedWith(compareBy { (_, path) ->
      when {
        path == currentPath -> -1
        path.deleted -> 1
        else -> 0
      }
    })
  }
}

private fun <T> getPreviousNode(stack: Stack<Pair<Int, T>>): Int {
  return if (stack.size < 2) {
    Dfs.NextNode.NODE_NOT_FOUND
  }
  else stack[stack.size - 2].first
}

private fun <T> isDown(stack: Stack<Pair<Int, T>>): Boolean {
  val (currentNode, _) = stack.peek()
  val previousNode = getPreviousNode(stack)
  if (previousNode == Dfs.NextNode.NODE_NOT_FOUND) return true
  return previousNode < currentNode
}