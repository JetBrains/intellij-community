// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.graph.linearBek

import com.intellij.openapi.diagnostic.Logger
import com.intellij.vcs.log.graph.actions.GraphAction
import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.GraphLayout
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.impl.facade.BekBaseController
import com.intellij.vcs.log.graph.impl.facade.CascadeController
import com.intellij.vcs.log.graph.impl.facade.GraphChangesUtil
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAction
import com.intellij.vcs.log.graph.impl.facade.LinearGraphController.LinearGraphAnswer
import com.intellij.vcs.log.graph.impl.facade.bek.BekIntMap
import com.intellij.vcs.log.graph.linearBek.LinearBekGraph.WorkingLinearBekGraph
import com.intellij.vcs.log.graph.linearBek.LinearBekGraphBuilder.MergeFragment
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import java.util.*

class LinearBekController(controller: BekBaseController, permanentGraphInfo: PermanentGraphInfo<*>) : CascadeController(controller,
                                                                                                                        permanentGraphInfo) {
  private val delegateGraph: LinearGraph
    get() = delegateController.compiledGraph

  private val compiledGraph: LinearBekGraph = LinearBekGraph(delegateGraph)
  private val bekGraphLayout: BekGraphLayout = BekGraphLayout(permanentGraphInfo.permanentGraphLayout, controller.bekIntMap)
  private val linearBekGraphBuilder: LinearBekGraphBuilder = LinearBekGraphBuilder(compiledGraph, bekGraphLayout)

  init {
    val start = System.currentTimeMillis()
    linearBekGraphBuilder.collapseAll()
    LOG.debug("Linear bek took " + (System.currentTimeMillis() - start) / 1000.0 + " sec")
  }

  override fun delegateGraphChanged(delegateAnswer: LinearGraphAnswer): LinearGraphAnswer {
    return delegateAnswer
  }

  override fun performAction(action: LinearGraphAction): LinearGraphAnswer? {
    if (action.affectedElement != null) {
      if (action.type == GraphAction.Type.MOUSE_CLICK) {
        val graphElement = action.affectedElement!!.graphElement
        if (graphElement is GraphNode) {
          val answer = collapseNode(graphElement)
          if (answer != null) return answer
          for (dottedEdge in getAllAdjacentDottedEdges(graphElement)) {
            val expandedAnswer = expandEdge(dottedEdge)
            if (expandedAnswer != null) return expandedAnswer
          }
        }
        else if (graphElement is GraphEdge) {
          return expandEdge(graphElement)
        }
      }
      else if (action.type == GraphAction.Type.MOUSE_OVER) {
        val graphElement = action.affectedElement!!.graphElement
        if (graphElement is GraphNode) {
          val answer = highlightNode(graphElement)
          if (answer != null) return answer
          for (dottedEdge in getAllAdjacentDottedEdges(graphElement)) {
            val highlightAnswer = highlightEdge(dottedEdge)
            if (highlightAnswer != null) return highlightAnswer
          }
        }
        else if (graphElement is GraphEdge) {
          return highlightEdge(graphElement)
        }
      }
    }
    else if (action.type == GraphAction.Type.BUTTON_COLLAPSE) {
      return collapseAll()
    }
    else if (action.type == GraphAction.Type.BUTTON_EXPAND) {
      return expandAll()
    }
    return null
  }

  private fun getAllAdjacentDottedEdges(graphElement: GraphNode): List<GraphEdge> {
    return compiledGraph.getAdjacentEdges(graphElement.nodeIndex, EdgeFilter.ALL).filter { graphEdge: GraphEdge ->
      graphEdge.type == GraphEdgeType.DOTTED
    }
  }

  private fun expandAll(): LinearGraphAnswer {
    return object : LinearGraphAnswer(GraphChangesUtil.SOME_CHANGES) {
      override fun getGraphUpdater(): Runnable {
        return Runnable {
          compiledGraph.myDottedEdges.removeAll()
          compiledGraph.myHiddenEdges.removeAll()
        }
      }
    }
  }

  private fun collapseAll(): LinearGraphAnswer {
    val workingGraph = WorkingLinearBekGraph(compiledGraph)
    LinearBekGraphBuilder(workingGraph, bekGraphLayout).collapseAll()
    return object : LinearGraphAnswer(
      GraphChangesUtil.edgesReplaced(workingGraph.removedEdges, workingGraph.addedEdges, delegateGraph)) {
      override fun getGraphUpdater(): Runnable {
        return Runnable { workingGraph.applyChanges() }
      }
    }
  }

  private fun highlightNode(node: GraphNode): LinearGraphAnswer? {
    val toCollapse = collectFragmentsToCollapse(node)
    if (toCollapse.isEmpty()) return null
    val toHighlight: MutableSet<Int> = HashSet()
    for (fragment in toCollapse) {
      toHighlight.addAll(fragment.allNodes)
    }
    return LinearGraphUtils.createSelectedAnswer(compiledGraph, toHighlight)
  }

  private fun highlightEdge(edge: GraphEdge): LinearGraphAnswer? {
    if (edge.type != GraphEdgeType.DOTTED) return null
    return LinearGraphUtils.createSelectedAnswer(compiledGraph, setOf(edge.upNodeIndex, edge.downNodeIndex))
  }

  private fun collapseNode(node: GraphNode): LinearGraphAnswer? {
    val toCollapse = collectNodesToCollapse(node)
    if (toCollapse.isEmpty()) return null
    for (i in toCollapse) {
      linearBekGraphBuilder.collapseFragment(i)
    }
    return LinearGraphAnswer(GraphChangesUtil.SOME_CHANGES)
  }

  private fun collectNodesToCollapse(node: GraphNode): SortedSet<Int> {
    val toCollapse: SortedSet<Int> = TreeSet(Comparator.reverseOrder())
    for (f in collectFragmentsToCollapse(node)) {
      toCollapse.add(f.parent)
      toCollapse.addAll(f.tailsAndBody)
    }
    return toCollapse
  }

  private fun collectFragmentsToCollapse(node: GraphNode): Set<MergeFragment> {
    val result: MutableSet<MergeFragment> = HashSet()
    var mergesCount = 0
    val toProcess = LinkedHashSet<Int>()
    toProcess.add(node.nodeIndex)
    while (toProcess.isNotEmpty()) {
      val i = toProcess.first()
      toProcess.remove(i)
      val fragment = linearBekGraphBuilder.getFragment(i) ?: continue
      result.add(fragment)
      toProcess.addAll(fragment.tailsAndBody)
      mergesCount++
      if (mergesCount > 10) break
    }
    return result
  }

  private fun expandEdge(edge: GraphEdge): LinearGraphAnswer? {
    if (edge.type != GraphEdgeType.DOTTED) return null
    return LinearGraphAnswer(GraphChangesUtil.edgesReplaced(setOf(edge), compiledGraph.expandEdge(edge), delegateGraph))
  }

  override fun getCompiledGraph(): LinearGraph = compiledGraph

  private class BekGraphLayout(private val graphLayout: GraphLayout,
                               private val bekIntMap: BekIntMap) : GraphLayout {
    override fun getLayoutIndex(nodeIndex: Int): Int {
      return graphLayout.getLayoutIndex(bekIntMap.getUsualIndex(nodeIndex))
    }

    override fun getOneOfHeadNodeIndex(nodeIndex: Int): Int {
      val usualIndex = graphLayout.getOneOfHeadNodeIndex(bekIntMap.getUsualIndex(nodeIndex))
      return bekIntMap.getBekIndex(usualIndex)
    }

    override fun getHeadNodeIndex(): List<Int> = graphLayout.headNodeIndex.map { bekIntMap.getBekIndex(it) }
  }

  companion object {
    private val LOG = Logger.getInstance(LinearBekController::class.java)
  }
}