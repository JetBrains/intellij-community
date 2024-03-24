// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator.Companion.update
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.UnsignedBitSet

class FilteredController(delegateLinearGraphController: LinearGraphController,
                         permanentGraphInfo: PermanentGraphInfo<*>,
                         matchedIds: Set<Int>,
                         visibleHeadsIds: Set<Int>? = null) :
  CascadeController(delegateLinearGraphController, permanentGraphInfo) {

  val collapsedGraph: CollapsedGraph

  init {
    val initVisibility = UnsignedBitSet()
    if (visibleHeadsIds != null) {
      DfsWalk(visibleHeadsIds, permanentGraphInfo.linearGraph).walk(true) { node ->
        if (matchedIds.contains(node)) initVisibility[node] = true
        true
      }
    }
    else {
      for (matchedId in matchedIds) initVisibility[matchedId] = true
    }

    collapsedGraph = CollapsedGraph.newInstance(delegateLinearGraphController.compiledGraph, initVisibility)
    update(collapsedGraph, 0, collapsedGraph.delegatedGraph.nodesCount() - 1)
  }

  override fun performLinearGraphAction(action: LinearGraphController.LinearGraphAction): LinearGraphController.LinearGraphAnswer {
    // filter prohibits any actions on delegate graph for now
    return performAction(action) ?: LinearGraphUtils.DEFAULT_GRAPH_ANSWER
  }

  override fun convertToDelegate(graphElement: GraphElement): GraphElement? {
    // filter prohibits any actions on delegate graph for now
    return null
  }

  override fun delegateGraphChanged(delegateAnswer: LinearGraphController.LinearGraphAnswer): LinearGraphController.LinearGraphAnswer {
    if (delegateAnswer === LinearGraphUtils.DEFAULT_GRAPH_ANSWER) return delegateAnswer
    throw UnsupportedOperationException() // todo fix later
  }

  override fun performAction(action: LinearGraphController.LinearGraphAction) = null

  override fun getCompiledGraph() = collapsedGraph.compiledGraph
}
