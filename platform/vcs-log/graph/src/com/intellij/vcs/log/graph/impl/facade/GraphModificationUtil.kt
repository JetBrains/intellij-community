// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.collapsing.CollapsedGraph
import com.intellij.vcs.log.graph.collapsing.DottedFilterEdgesGenerator
import com.intellij.vcs.log.graph.impl.facade.CascadeController.Companion.performActionRecursively
import org.jetbrains.annotations.ApiStatus

fun <CommitId> hideCommits(graphController: LinearGraphController,
                           permanentGraphInfo: PermanentGraphInfo<CommitId>,
                           commitsToHide: Set<CommitId>): Boolean {
  return modifyGraph(graphController) { graph ->
    val nodeIds = permanentGraphInfo.permanentCommitsInfo.convertToNodeIds(commitsToHide)
    val rowsToHide = nodeIds.mapNotNullTo(mutableSetOf()) { graph.compiledGraph.getNodeIndex(it) }
    DottedFilterEdgesGenerator.hideInplace(graph, rowsToHide)
  }
}

fun modifyGraph(graphController: LinearGraphController, consumer: (CollapsedGraph) -> Unit): Boolean {
  val result = graphController.performActionRecursively action@{ cc ->
    if (cc is FilteredController) {
      consumer(cc.collapsedGraph)
      return@action GraphChangesUtil.SOME_CHANGES
    }
    return@action null
  }
  return result != null
}

fun CollapsedGraph.modify(modifier: CollapsedGraph.Modification.() -> Unit) {
  val modification = startModification()
  modifier.invoke(modification)
  modification.apply()
}

fun CollapsedGraph.Modification.hideRow(row: Int) {
  hideNode(convertToDelegateNodeIndex(row))
}

fun CollapsedGraph.Modification.connectRows(upRow: Int, downRow: Int) {
  createEdge(GraphEdge(convertToDelegateNodeIndex(upRow), convertToDelegateNodeIndex(downRow), null, GraphEdgeType.DOTTED))
}