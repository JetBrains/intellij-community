// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils

import com.intellij.openapi.util.Ref
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import gnu.trove.TIntHashSet
import kotlin.math.max

/**
 * Get nodes reachable from the specified by down edges of the graph.
 * @return reachable nodes or all nodes if startNodes is null
 */
fun LinearGraph.getReachableNodes(startNodes: Set<Int>?): UnsignedBitSet {
  if (startNodes == null) {
    val nodesVisibility = UnsignedBitSet()
    nodesVisibility.set(0, nodesCount() - 1, true)
    return nodesVisibility
  }

  val result = UnsignedBitSet()
  DfsWalk(startNodes, this).walk(true) { node: Int ->
    result.set(node, true)
    true
  }
  return result
}

/**
 * Check whether lowerNode is an ancestor of the upperNode.
 */
fun LiteLinearGraph.isAncestor(lowerNode: Int, upperNode: Int): Boolean {
  val visited = BitSetFlags(nodesCount(), false)

  val result = Ref.create(false)
  walk(lowerNode) { currentNode ->
    visited.set(currentNode, true)

    if (currentNode == upperNode) {
      result.set(true)
      return@walk Dfs.NextNode.EXIT
    }
    if (currentNode > upperNode) {
      for (nextNode in getNodes(currentNode, LiteLinearGraph.NodeFilter.UP)) {
        if (!visited.get(nextNode)) {
          return@walk nextNode
        }
      }
    }

    Dfs.NextNode.NODE_NOT_FOUND
  }

  return result.get()
}

/**
 * Find a parent of the startNode which is on the path to the endNode.
 * Fails when there is no path from startNode to endNode.
 */
fun LiteLinearGraph.getCorrespondingParent(startNode: Int, endNode: Int, visited: Flags): Int {
  val candidates = getNodes(startNode, LiteLinearGraph.NodeFilter.DOWN)
  if (candidates.size == 1) return candidates[0]
  if (candidates.contains(endNode)) return endNode

  val bfsWalks = candidates.mapTo(mutableListOf()) { BfsWalk(it, this, visited) }

  visited.setAll(false)
  do {
    for (walk in bfsWalks) {
      if (walk.step().contains(endNode)) {
        return walk.start
      }
    }
    bfsWalks.removeIf { it.isFinished() }
  }
  while (bfsWalks.isNotEmpty())

  return candidates[0]
}

/**
 * Return a set of nodes that are reachable from the first node, but not from the second.
 */
fun LinearGraph.subgraphDifference(node1: Int, node2: Int): TIntHashSet {
  val liteLinearGraph = LinearGraphUtils.asLiteLinearGraph(this)

  val visited2 = BitSetFlags(nodesCount())
  val bfsWalk2 = BfsWalk(node2, liteLinearGraph, visited2)
  val visited1 = object : TIntHashSetFlags(nodesCount()) {
    override fun get(index: Int): Boolean {
      return super.get(index) || visited2[index] || bfsWalk2.currentNodes().contains(index)
    }
  }
  val bfsWalk1 = BfsWalk(node1, liteLinearGraph, visited1)

  var max1 = bfsWalk1.currentNodes().maxOrDefault()
  var min2 = bfsWalk2.currentNodes().minOrDefault()
  while (!bfsWalk1.isFinished()) {
    if (max1 < min2) {
      max1 = max(max1, bfsWalk1.step().maxOrDefault())
    }
    else {
      bfsWalk2.step()
      min2 = bfsWalk2.currentNodes().minOrDefault()
    }
  }

  return visited1.data
}

private fun Iterable<Int>.minOrDefault(): Int {
  return min() ?: Int.MAX_VALUE
}

private fun Iterable<Int>.maxOrDefault(): Int {
  return max() ?: Int.MIN_VALUE
}