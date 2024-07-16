// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

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
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

internal class FileHistoryRefiner(private val visibleLinearGraph: LinearGraph,
                                  permanentGraphInfo: PermanentGraphInfo<Int>,
                                  private val historyData: FileHistoryData) {
  private val permanentCommitsInfo: PermanentCommitsInfo<Int> = permanentGraphInfo.permanentCommitsInfo
  private val permanentLinearGraph: LiteLinearGraph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)

  private val visibilityBuffer = BitSetFlags(permanentLinearGraph.nodesCount()) // a reusable buffer for bfs
  private val commitToFileStateMap = HashMap<Int, CommitFileState>()

  fun refine(row: Int, startFileState: CommitFileState): Pair<Map<Int, CommitFileState>, Set<Int>> {
    walk(LinearGraphUtils.asLiteLinearGraph(visibleLinearGraph), row, startFileState)

    val excluded = HashSet<Int>()
    for ((commit, path) in commitToFileStateMap) {
      if (!historyData.affects(commit, path, true)) {
        excluded.add(commit)
      }
    }

    excluded.forEach { commitToFileStateMap.remove(it) }
    return Pair(commitToFileStateMap, excluded)
  }

  /*
   * Depth-first walk for a graph. For each node, walks both into upward and downward siblings.
   * Tries to preserve direction of travel: when a node is entered from up-sibling, goes to the down-siblings first.
   * Then goes to the other up-siblings.
   * And when a node is entered from down-sibling, goes to the up-siblings first.
   * Then goes to the other down-siblings.
   */
  private fun walk(graph: LiteLinearGraph, startNode: Int, startFileState: CommitFileState) {
    if (startNode < 0 || startNode >= graph.nodesCount()) {
      return
    }

    val visited = BitSetFlags(graph.nodesCount(), false)
    val starts: Queue<Pair<Int, CommitFileState>> = LinkedList<Pair<Int, CommitFileState>>().apply {
      add(Pair(startNode, startFileState))
    }

    iterateStarts@ while (starts.isNotEmpty()) {
      val (nextStart, nextStartFileState) = starts.poll()
      if (visited.get(nextStart)) continue@iterateStarts

      val stack = Stack(Pair(nextStart, nextStartFileState))

      iterateStack@ while (!stack.empty()) {
        val (currentNode, currentFileState) = stack.peek()
        val down = isDown(stack)
        if (!visited.get(currentNode)) {
          visited.set(currentNode, true)
          commitToFileStateMap[permanentCommitsInfo.getCommitId(visibleLinearGraph.getNodeId(currentNode))] = currentFileState
        }

        for ((nextNode, nextFileState) in getNextNodes(graph, visited, currentNode, currentFileState, down)) {
          if (!currentFileState.deleted && nextFileState.deleted) {
            starts.add(Pair(nextNode, nextFileState))
          }
          else {
            stack.push(Pair(nextNode, nextFileState))
            continue@iterateStack
          }
        }

        for ((nextNode, nextFileState) in getNextNodes(graph, visited, currentNode, currentFileState, !down)) {
          if (!currentFileState.deleted && nextFileState.deleted) {
            starts.add(Pair(nextNode, nextFileState))
          }
          else {
            stack.push(Pair(nextNode, nextFileState))
            continue@iterateStack
          }
        }

        stack.pop()
      }
    }
  }

  private fun getFileState(currentNode: Int, previousNode: Int, previousFileState: CommitFileState, down: Boolean): CommitFileState {
    if (previousNode == Dfs.NextNode.NODE_NOT_FOUND) return previousFileState

    val previousNodeId = visibleLinearGraph.getNodeId(previousNode)
    val currentNodeId = visibleLinearGraph.getNodeId(currentNode)

    if (down) {
      val previousCommit = permanentCommitsInfo.getCommitId(previousNodeId)
      val stateGetter = { parentIndex: Int ->
        historyData.getFileStateInParentRevision(previousCommit, permanentCommitsInfo.getCommitId(parentIndex), previousFileState)
      }
      val state = findStateWithoutConflict(previousNodeId, stateGetter)
      return state ?: stateGetter(permanentLinearGraph.getCorrespondingParent(previousNodeId, currentNodeId, visibilityBuffer))
    }
    else {
      val currentCommit = permanentCommitsInfo.getCommitId(currentNodeId)
      val stateGetter = { parentIndex: Int ->
        historyData.getFileStateInChildRevision(currentCommit, permanentCommitsInfo.getCommitId(parentIndex), previousFileState)
      }
      val state = findStateWithoutConflict(currentNodeId, stateGetter)
      return state ?: stateGetter(permanentLinearGraph.getCorrespondingParent(currentNodeId, previousNodeId, visibilityBuffer))
    }
  }

  private fun findStateWithoutConflict(nodeId: Int, stateGetter: (Int) -> CommitFileState): CommitFileState? {
    val parents = permanentLinearGraph.getNodes(nodeId, LiteLinearGraph.NodeFilter.DOWN)
    val state = stateGetter(parents.first())
    if (parents.size == 1) return state

    if (parents.subList(1, parents.size).find { stateGetter(it) != state } != null) return null
    return state
  }

  private fun getNextNodes(graph: LiteLinearGraph,
                           visited: BitSetFlags,
                           currentNode: Int,
                           currentPath: CommitFileState,
                           down: Boolean): List<Pair<Int, CommitFileState>> {
    val nextNodes = graph.getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)
    val nodesWithStates = nextNodes.filterNot(visited::get).map { node -> Pair(node, getFileState(node, currentNode, currentPath, down)) }

    return nodesWithStates.sortedWith(compareBy { (_, path) ->
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