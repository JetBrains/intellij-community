// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.utils

import com.intellij.openapi.util.Ref
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus
import kotlin.math.max

/**
 * Get nodes reachable from the specified by down edges of the graph.
 * @return reachable nodes or all nodes if startNodes is null
 */
internal fun LinearGraph.getReachableNodes(startNodes: Set<Int>?): UnsignedBitSet {
  return getReachableMatchingNodes(startNodes, null)
}

/**
 * Get matching nodes reachable from the specified by down edges of the graph.
 * @return reachable matching nodes or all matching nodes if startNodes is null
 */
internal fun LinearGraph.getReachableMatchingNodes(startNodes: Set<Int>?, matchedNodes: Set<Int>?): UnsignedBitSet {
  val visibility = UnsignedBitSet()
  if (startNodes == null) {
    if (matchedNodes == null) {
      visibility.set(0, nodesCount() - 1, true)
    }
    else {
      for (matchedId in matchedNodes) visibility[matchedId] = true
    }
    return visibility
  }

  DfsWalk(startNodes, this).walk(true) { node ->
    if (matchedNodes == null || matchedNodes.contains(node)) visibility[node] = true
    true
  }
  return visibility
}

/**
 * Check whether lowerNode is an ancestor of the upperNode.
 */
@ApiStatus.Internal
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
@ApiStatus.Internal
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
@ApiStatus.Internal
fun LinearGraph.subgraphDifference(node1: Int, node2: Int): IntSet {
  val liteLinearGraph = LinearGraphUtils.asLiteLinearGraph(this)

  val visited2 = BitSetFlags(nodesCount())
  val bfsWalk2 = BfsWalk(node2, liteLinearGraph, visited2)
  val visited1 = object : IntHashSetFlags(nodesCount()) {
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
  return minOrNull() ?: Int.MAX_VALUE
}

private fun Iterable<Int>.maxOrDefault(): Int {
  return maxOrNull() ?: Int.MIN_VALUE
}

/**
 * Returns a set of nodes in the graph that are reachable only from the specified head node and not from others.
 */
@ApiStatus.Internal
fun LiteLinearGraph.exclusiveNodes(headNode: Int, isHead: (Int) -> Boolean = { false }): IntSet {
  val result = IntOpenHashSet()
  BfsWalk(headNode, this).walk { it ->
    val upNodes = getNodes(it, LiteLinearGraph.NodeFilter.UP)
    if ((upNodes.isEmpty() || upNodes.all { result.contains(it) }) &&
        (it == headNode || !isHead(it))) {
      result.add(it)
      true
    }
    else {
      false
    }
  }
  return result
}

/**
 * Returns a set of nodes in the graph that are reachable only from the specified head node and not from others.
 */
@ApiStatus.Internal
fun LinearGraph.exclusiveNodes(headNode: Int, isHead: (Int) -> Boolean = { false }): IntSet {
  return LinearGraphUtils.asLiteLinearGraph(this).exclusiveNodes(headNode, isHead)
}