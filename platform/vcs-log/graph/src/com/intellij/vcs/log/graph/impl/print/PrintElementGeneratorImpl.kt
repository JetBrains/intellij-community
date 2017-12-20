/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.vcs.log.graph.impl.print

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SLRUMap
import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator
import com.intellij.vcs.log.graph.api.printer.PrintElementManager
import com.intellij.vcs.log.graph.impl.print.elements.EdgePrintElementImpl
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement
import com.intellij.vcs.log.graph.impl.print.elements.SimplePrintElementImpl
import com.intellij.vcs.log.graph.impl.print.elements.TerminalEdgePrintElement
import com.intellij.vcs.log.graph.utils.LinearGraphUtils.*
import com.intellij.vcs.log.graph.utils.NormalEdge
import org.jetbrains.annotations.TestOnly
import java.util.*

class PrintElementGeneratorImpl @TestOnly constructor(private val linearGraph: LinearGraph,
                                                      private val printElementManager: PrintElementManager,
                                                      private val longEdgeSize: Int,
                                                      private val visiblePartSize: Int,
                                                      private val edgeWithArrowSize: Int) : PrintElementGenerator {
  private val cache = SLRUMap<Int, List<GraphElement>>(CACHE_SIZE, CACHE_SIZE * 2)
  private val edgesInRowGenerator = EdgesInRowGenerator(linearGraph)
  private val elementComparator: Comparator<GraphElement>
    get() = printElementManager.graphElementComparator

  private var recommendedWidth = 0

  constructor(graph: LinearGraph,
              printElementManager: PrintElementManager,
              showLongEdges: Boolean) :
    this(graph, printElementManager,
         if (showLongEdges) VERY_LONG_EDGE_SIZE else LONG_EDGE_SIZE,
         if (showLongEdges) VERY_LONG_EDGE_PART_SIZE else LONG_EDGE_PART_SIZE,
         if (showLongEdges) LONG_EDGE_SIZE else Integer.MAX_VALUE)

  fun getRecommendedWidth(): Int {
    if (recommendedWidth <= 0) {
      val n = Math.min(SAMPLE_SIZE, linearGraph.nodesCount())

      var sum = 0.0
      var sumSquares = 0.0
      var edgesCount = 0
      val currentNormalEdges = ContainerUtil.newHashSet<NormalEdge>()

      for (i in 0 until n) {
        val adjacentEdges = linearGraph.getAdjacentEdges(i, EdgeFilter.ALL)
        var upArrows = 0
        var downArrows = 0
        for (e in adjacentEdges) {
          val normalEdge = asNormalEdge(e)
          if (normalEdge != null) {
            if (isEdgeUp(e, i)) {
              currentNormalEdges.remove(normalEdge)
            }
            else {
              currentNormalEdges.add(normalEdge)
            }
          }
          else {
            if (e.type == GraphEdgeType.DOTTED_ARROW_UP) {
              upArrows++
            }
            else {
              downArrows++
            }
          }
        }

        var newEdgesCount = 0
        for (e in currentNormalEdges) {
          if (isEdgeVisibleInRow(e, i)) {
            newEdgesCount++
          }
          else {
            val arrow = getArrowType(e, i)
            if (arrow === EdgePrintElement.Type.DOWN) {
              downArrows++
            }
            else if (arrow === EdgePrintElement.Type.UP) {
              upArrows++
            }
          }
        }

        /*
         * 0 <= K < 1; weight is an arithmetic progression, starting at 2 / ( n * (k + 1)) ending at k * 2 / ( n * (k + 1))
         * this formula ensures that sum of all weights is 1
         */
        val width = Math.max(edgesCount + upArrows, newEdgesCount + downArrows)
        val weight = 2 / (n * (K + 1)) * (1 + (K - 1) * i / (n - 1))
        sum += width * weight
        sumSquares += width.toDouble() * width.toDouble() * weight

        edgesCount = newEdgesCount
      }

      /*
      weighted variance calculation described here:
      http://stackoverflow.com/questions/30383270/how-do-i-calculate-the-standard-deviation-between-weighted-measurements
       s*/
      val average = sum
      val deviation = Math.sqrt(sumSquares - average * average)
      recommendedWidth = Math.round(average + deviation).toInt()
    }

    return recommendedWidth
  }

  private fun collectElements(rowIndex: Int, builder: PrintElementBuilder) {
    val visibleElements = getSortedVisibleElementsInRow(rowIndex)
    val upPosition = createEndPositionFunction(rowIndex - 1, true)
    val downPosition = createEndPositionFunction(rowIndex + 1, false)

    visibleElements.forEachIndexed { position, element ->
      when (element) {
        is GraphNode -> {
          val nodeIndex = element.nodeIndex
          builder.consumeNode(element, position)
          linearGraph.getAdjacentEdges(nodeIndex, EdgeFilter.ALL).forEach { edge ->
            val arrowType = getArrowType(edge, rowIndex)
            val down = downPosition(edge)
            val up = upPosition(edge)
            if (down != null) {
              builder.consumeDownEdge(edge, position, down, arrowType === EdgePrintElement.Type.DOWN)
            }
            if (up != null) {
              builder.consumeUpEdge(edge, up, position, arrowType === EdgePrintElement.Type.UP)
            }
          }
        }
        is GraphEdge -> {
          val arrowType = getArrowType(element, rowIndex)
          val down = downPosition(element)
          val up = upPosition(element)
          if (down != null) {
            builder.consumeDownEdge(element, position, down, arrowType === EdgePrintElement.Type.DOWN)
          }
          else if (arrowType === EdgePrintElement.Type.DOWN) {
            builder.consumeArrow(element, position, arrowType)
          }
          if (up != null) {
            builder.consumeUpEdge(element, up, position, arrowType === EdgePrintElement.Type.UP)
          }
          else if (arrowType === EdgePrintElement.Type.UP) {
            builder.consumeArrow(element, position, arrowType)
          }
        }
      }
    }
  }

  private fun createEndPositionFunction(visibleRowIndex: Int, up: Boolean): (GraphEdge) -> Int? {
    if (visibleRowIndex < 0 || visibleRowIndex >= linearGraph.nodesCount()) return { _ -> null }

    val visibleElementsInNextRow = getSortedVisibleElementsInRow(visibleRowIndex)

    val toPosition = HashMap<GraphElement, Int>(visibleElementsInNextRow.size)
    visibleElementsInNextRow.forEachIndexed { position, element -> toPosition.put(element, position) }

    return { edge ->
      toPosition[edge] ?: run {
        val nodeIndex = if (up) edge.upNodeIndex else edge.downNodeIndex
        if (nodeIndex != null) toPosition[linearGraph.getGraphNode(nodeIndex)]
        else null
      }
    }
  }

  private fun getArrowType(edge: GraphEdge, rowIndex: Int): EdgePrintElement.Type? {
    val normalEdge = asNormalEdge(edge)
    if (normalEdge != null) {
      return getArrowType(normalEdge, rowIndex)
    }
    else { // special edges
      when (edge.type) {
        GraphEdgeType.DOTTED_ARROW_DOWN, GraphEdgeType.NOT_LOAD_COMMIT ->
          if (intEqual(edge.upNodeIndex, rowIndex - 1)) {
            return EdgePrintElement.Type.DOWN
          }
        GraphEdgeType.DOTTED_ARROW_UP ->
          // todo case 0-row arrow
          if (intEqual(edge.downNodeIndex, rowIndex + 1)) {
            return EdgePrintElement.Type.UP
          }
        else -> LOG.error("Unknown special edge type " + edge.type + " at row " + rowIndex)
      }
    }
    return null
  }

  private fun getArrowType(normalEdge: NormalEdge, rowIndex: Int): EdgePrintElement.Type? {
    val edgeSize = normalEdge.down - normalEdge.up
    val upOffset = rowIndex - normalEdge.up
    val downOffset = normalEdge.down - rowIndex

    if (edgeSize >= longEdgeSize) {
      if (upOffset == visiblePartSize) {
        LOG.assertTrue(downOffset != visiblePartSize,
                       "Both up and down arrow at row " + rowIndex) // this can not happen due to how constants are picked out, but just in case
        return EdgePrintElement.Type.DOWN
      }
      if (downOffset == visiblePartSize) return EdgePrintElement.Type.UP
    }
    if (edgeSize >= edgeWithArrowSize) {
      if (upOffset == 1) {
        LOG.assertTrue(downOffset != 1, "Both up and down arrow at row " + rowIndex)
        return EdgePrintElement.Type.DOWN
      }
      if (downOffset == 1) return EdgePrintElement.Type.UP
    }
    return null
  }

  private fun isEdgeVisibleInRow(edge: GraphEdge, visibleRowIndex: Int): Boolean {
    val normalEdge = asNormalEdge(edge) ?:
                     return false // e.d. edge is special. See addSpecialEdges
    return isEdgeVisibleInRow(normalEdge, visibleRowIndex)
  }

  private fun isEdgeVisibleInRow(normalEdge: NormalEdge, visibleRowIndex: Int): Boolean {
    return normalEdge.down - normalEdge.up < longEdgeSize || getAttachmentDistance(normalEdge, visibleRowIndex) <= visiblePartSize
  }

  private fun getSortedVisibleElementsInRow(rowIndex: Int): List<GraphElement> {
    val graphElements = cache.get(rowIndex)
    if (graphElements != null) {
      return graphElements
    }

    val result = ArrayList<GraphElement>()
    result.add(linearGraph.getGraphNode(rowIndex))

    edgesInRowGenerator.getEdgesInRow(rowIndex).filterTo(result) { isEdgeVisibleInRow(it, rowIndex) }
    if (rowIndex > 0) {
      linearGraph.getAdjacentEdges(rowIndex - 1, EdgeFilter.SPECIAL)
        .filterTo(result) { isEdgeDown(it, rowIndex - 1) }
    }
    if (rowIndex < linearGraph.nodesCount() - 1) {
      linearGraph.getAdjacentEdges(rowIndex + 1, EdgeFilter.SPECIAL)
        .filterTo(result) { isEdgeUp(it, rowIndex + 1) }
    }

    Collections.sort(result, elementComparator)
    cache.put(rowIndex, result)
    return result
  }

  override fun getPrintElements(rowIndex: Int): Collection<PrintElementWithGraphElement> {
    val builder = PrintElementBuilder(rowIndex)
    collectElements(rowIndex, builder)
    return builder.build()
  }

  private fun getAttachmentDistance(e1: NormalEdge, rowIndex: Int): Int {
    return Math.min(rowIndex - e1.up, e1.down - rowIndex)
  }

  private inner class PrintElementBuilder(private val rowIndex: Int) {

    private val result = ArrayList<PrintElementWithGraphElement>()
    private val nodes = ArrayList<PrintElementWithGraphElement>() // nodes at the end, to be drawn over the edges
    fun consumeNode(node: GraphNode, position: Int) {
      nodes.add(SimplePrintElementImpl(rowIndex, position, node, printElementManager))
    }

    fun consumeDownEdge(edge: GraphEdge, upPosition: Int, downPosition: Int, hasArrow: Boolean) {
      result.add(EdgePrintElementImpl(rowIndex, upPosition, downPosition, EdgePrintElement.Type.DOWN, edge, hasArrow,
                                      printElementManager))
    }

    fun consumeUpEdge(edge: GraphEdge, upPosition: Int, downPosition: Int, hasArrow: Boolean) {
      result.add(EdgePrintElementImpl(rowIndex, downPosition, upPosition, EdgePrintElement.Type.UP, edge, hasArrow,
                                      printElementManager))
    }

    fun consumeArrow(edge: GraphEdge, position: Int, arrowType: EdgePrintElement.Type) {
      result.add(TerminalEdgePrintElement(rowIndex, position,
                                          arrowType, edge,
                                          printElementManager))
    }

    fun build(): Collection<PrintElementWithGraphElement> {
      result.addAll(nodes)
      return result
    }
  }

  companion object {
    private val LOG = Logger.getInstance(PrintElementGeneratorImpl::class.java)

    private val VERY_LONG_EDGE_SIZE = 1000
    @JvmField
    val LONG_EDGE_SIZE = 30
    private val VERY_LONG_EDGE_PART_SIZE = 250
    private val LONG_EDGE_PART_SIZE = 1

    private val CACHE_SIZE = 100
    private val SAMPLE_SIZE = 20000
    private val K = 0.1
  }
}
