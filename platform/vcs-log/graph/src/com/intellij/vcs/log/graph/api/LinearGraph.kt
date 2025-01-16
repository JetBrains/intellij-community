// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.api;

import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphNode

interface LinearGraph {
  fun nodesCount(): Int

  fun getAdjacentEdges(nodeIndex: Int, filter: EdgeFilter): List<GraphEdge>

  fun getGraphNode(nodeIndex: Int): GraphNode

  fun getNodeId(nodeIndex: Int): Int

  // return null, if node doesn't exist
  fun getNodeIndex(nodeId: Int): Int?
}
