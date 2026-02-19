// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator
import com.intellij.vcs.log.graph.collapsing.EdgeStorageWrapper
import com.intellij.vcs.log.graph.impl.permanent.GraphLayoutBuilder.getHeads
import com.intellij.vcs.log.graph.utils.Dfs
import com.intellij.vcs.log.graph.utils.UnsignedBitSet
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@ApiStatus.Internal
object FirstParentController {
  fun create(delegateController: LinearGraphController, permanentGraphInfo: PermanentGraphInfo<*>,
             matchedIds: Set<Int>?, visibleHeadsIds: Set<Int>? = null): FilteredController {
    val linearGraph = permanentGraphInfo.linearGraph
    val startNodes = visibleHeadsIds ?: (permanentGraphInfo.branchNodeIds + linearGraph.getHeads())
    return FilteredController(delegateController, permanentGraphInfo) {
      buildCollapsedGraph(linearGraph, startNodes, matchedIds)
    }
  }

  @VisibleForTesting
  fun buildCollapsedGraph(linearGraph: LinearGraph, startNodes: Set<Int>, matchedNodes: Set<Int>?): CollapsedGraph {
    val (visibleNodes, hiddenEdges) = linearGraph.getVisibleNodesAndHiddenEdges(startNodes, matchedNodes)

    val collapsedGraph = CollapsedGraph.newInstance(LinearGraphWrapper(linearGraph, hiddenEdges = hiddenEdges), visibleNodes)
    if (matchedNodes != null) {
      DottedFilterEdgesGenerator.update(collapsedGraph, 0, collapsedGraph.delegatedGraph.nodesCount() - 1)
    }
    return collapsedGraph
  }

  private fun LinearGraph.getVisibleNodesAndHiddenEdges(startNodes: Set<Int>, matchedNodes: Set<Int>?): Pair<UnsignedBitSet, EdgeStorageWrapper> {
    val visibleNodes = UnsignedBitSet()
    val hiddenEdges = EdgeStorageWrapper.createSimpleEdgeStorage()

    val visited = BitSetFlags(nodesCount())
    for (start in startNodes) {
      if (start < 0) continue

      var node = start
      while (node != Dfs.NextNode.NODE_NOT_FOUND) {
        if (visited[node]) break
        visited[node] = true

        if (matchedNodes == null || matchedNodes.contains(node)) visibleNodes[node] = true

        val downEdges = getAdjacentEdges(node, EdgeFilter.NORMAL_DOWN)
        downEdges.drop(1).forEach { hiddenEdges.createEdge(it) }
        node = downEdges.firstOrNull()?.downNodeIndex ?: Dfs.NextNode.NODE_NOT_FOUND
      }
    }
    return Pair(visibleNodes, hiddenEdges)
  }
}