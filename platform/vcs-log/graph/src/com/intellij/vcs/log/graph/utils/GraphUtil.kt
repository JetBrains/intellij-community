// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.utils

import com.intellij.openapi.util.Ref
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags

fun getReachableNodes(graph: LinearGraph, headNodes: Set<Int>?): UnsignedBitSet {
  if (headNodes == null) {
    val nodesVisibility = UnsignedBitSet()
    nodesVisibility.set(0, graph.nodesCount() - 1, true)
    return nodesVisibility
  }

  val result = UnsignedBitSet()
  DfsWalk(headNodes, graph).walk(true) { node: Int ->
    result.set(node, true)
    true
  }
  return result
}

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

fun getCorrespondingParent(graph: LiteLinearGraph, startNode: Int, endNode: Int, visited: Flags): Int {
  val candidates = graph.getNodes(startNode, LiteLinearGraph.NodeFilter.DOWN)
  if (candidates.size == 1) return candidates[0]
  if (candidates.contains(endNode)) return endNode

  val bfsWalks = candidates.mapTo(mutableListOf()) { BfsWalk(it, graph, visited) }

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