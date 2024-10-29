// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.collapsing.EdgeStorageWrapper
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class LinearGraphWrapper @JvmOverloads constructor(val graph: LinearGraph,
                                                        val hiddenEdges: EdgeStorageWrapper = EdgeStorageWrapper.createSimpleEdgeStorage(),
                                                        val dottedEdges: EdgeStorageWrapper = EdgeStorageWrapper.createSimpleEdgeStorage()) : LinearGraph {
  override fun getAdjacentEdges(nodeIndex: Int, filter: EdgeFilter): List<GraphEdge> {
    return buildList {
      addAll(dottedEdges.getAdjacentEdges(nodeIndex, filter))
      addAll(graph.getAdjacentEdges(nodeIndex, filter))
      removeAll(hiddenEdges.getAdjacentEdges(nodeIndex, filter))
    }
  }

  override fun nodesCount() = graph.nodesCount()
  override fun getGraphNode(nodeIndex: Int) = graph.getGraphNode(nodeIndex)
  override fun getNodeId(nodeIndex: Int) = graph.getNodeId(nodeIndex)
  override fun getNodeIndex(nodeId: Int) = graph.getNodeIndex(nodeId)
}