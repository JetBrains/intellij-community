// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.getReachableMatchingNodes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FilteredController(delegateLinearGraphController: LinearGraphController, permanentGraphInfo: PermanentGraphInfo<*>,
                         buildCollapsedGraph: () -> CollapsedGraph) :
  CascadeController(delegateLinearGraphController, permanentGraphInfo) {

  val collapsedGraph: CollapsedGraph = buildCollapsedGraph()

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

  override val compiledGraph: LinearGraph get() = collapsedGraph.compiledGraph

  companion object {
    fun create(delegateController: LinearGraphController,
               permanentGraphInfo: PermanentGraphInfo<*>,
               matchedIds: Set<Int>,
               visibleHeadsIds: Set<Int>? = null): FilteredController {
      val visibility = permanentGraphInfo.linearGraph.getReachableMatchingNodes(visibleHeadsIds, matchedIds)
      return FilteredController(delegateController, permanentGraphInfo) {
        CollapsedGraph.newInstance(delegateController.compiledGraph, visibility).also {
          DottedFilterEdgesGenerator.update(it, 0, it.delegatedGraph.nodesCount() - 1)
        }
      }
    }
  }
}