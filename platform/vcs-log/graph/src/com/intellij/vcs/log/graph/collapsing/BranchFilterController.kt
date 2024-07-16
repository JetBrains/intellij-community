// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.collapsing

import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.CascadeController
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController
import com.intellij.vcs.log.graph.utils.getReachableNodes

class BranchFilterController(delegateLinearGraphController: LinearGraphController,
                             permanentGraphInfo: PermanentGraphInfo<*>,
                             idsOfVisibleBranches: Set<Int>?) : CascadeController(delegateLinearGraphController, permanentGraphInfo) {
  private var collapsedGraph: CollapsedGraph
  private val visibility = permanentGraphInfo.linearGraph.getReachableNodes(idsOfVisibleBranches)

  init {
    collapsedGraph = update()
  }

  private fun update(): CollapsedGraph {
    return CollapsedGraph.newInstance(delegateController.compiledGraph, visibility)
  }

  override fun delegateGraphChanged(delegateAnswer: LinearGraphController.LinearGraphAnswer): LinearGraphController.LinearGraphAnswer {
    if (delegateAnswer.graphChanges != null) collapsedGraph = update()
    return delegateAnswer
  }

  override fun performAction(action: LinearGraphController.LinearGraphAction) = null

  override val compiledGraph: LinearGraph get() = collapsedGraph.compiledGraph

  override fun convertToDelegate(graphElement: GraphElement): GraphElement? {
    return CollapsedController.convertToDelegate(graphElement, collapsedGraph)
  }
}
