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
import java.util.HashMap

internal class FileHistoryRefiner(private val visibleLinearGraph: LinearGraph,
                                  permanentGraphInfo: PermanentGraphInfo<Int>,
                                  private val historyData: FileHistoryData) : NodeVisitor {
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

private interface NodeVisitor {
  fun enterNode(node: Int, previousNode: Int, travelDirection: Boolean)
  fun exitNode(node: Int)
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
private fun LiteLinearGraph.walk(start: Int, visitor: NodeVisitor) {
  if (start < 0 || start >= nodesCount()) return

  val visited = BitSetFlags(nodesCount(), false)

  val stack = IntStack()
  stack.push(start) // commit + direction of travel

  outer@ while (!stack.empty()) {
    val currentNode = stack.peek()
    val down = isDown(stack)
    if (!visited.get(currentNode)) {
      visited.set(currentNode, true)
      visitor.enterNode(currentNode, getPreviousNode(stack), down)
    }

    for (nextNode in getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP)) {
      if (!visited.get(nextNode)) {
        stack.push(nextNode)
        continue@outer
      }
    }

    for (nextNode in getNodes(currentNode, if (down) LiteLinearGraph.NodeFilter.UP else LiteLinearGraph.NodeFilter.DOWN)) {
      if (!visited.get(nextNode)) {
        stack.push(nextNode)
        continue@outer
      }
    }

    visitor.exitNode(currentNode)
    stack.pop()
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