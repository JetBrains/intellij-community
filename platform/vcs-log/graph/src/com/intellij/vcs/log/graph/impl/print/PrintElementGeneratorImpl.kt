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
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SLRUMap
import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.printer.PrintElementManager
import com.intellij.vcs.log.graph.utils.LinearGraphUtils.*
import com.intellij.vcs.log.graph.utils.NormalEdge
import org.jetbrains.annotations.TestOnly
import java.util.*

class PrintElementGeneratorImpl : AbstractPrintElementGenerator {

  private val myCache = SLRUMap<Int, List<GraphElement>>(CACHE_SIZE, CACHE_SIZE * 2)
  private val myEdgesInRowGenerator: EdgesInRowGenerator
  private val myGraphElementComparator: Comparator<GraphElement>

  private val myLongEdgeSize: Int
  private val myVisiblePartSize: Int
  private val myEdgeWithArrowSize: Int
  private var myRecommendedWidth = 0

  constructor(graph: LinearGraph, printElementManager: PrintElementManager, showLongEdges: Boolean) : super(graph, printElementManager) {
    myEdgesInRowGenerator = EdgesInRowGenerator(graph)
    myGraphElementComparator = printElementManager.graphElementComparator
    if (showLongEdges) {
      myLongEdgeSize = VERY_LONG_EDGE_SIZE
      myVisiblePartSize = VERY_LONG_EDGE_PART_SIZE
      myEdgeWithArrowSize = if (SHOW_ARROW_WHEN_SHOW_LONG_EDGES) {
        LONG_EDGE_SIZE
      }
      else {
        Integer.MAX_VALUE
      }
    }
    else {
      myLongEdgeSize = LONG_EDGE_SIZE
      myVisiblePartSize = LONG_EDGE_PART_SIZE
      myEdgeWithArrowSize = Integer.MAX_VALUE
    }
  }

  @TestOnly
  constructor(graph: LinearGraph,
              printElementManager: PrintElementManager,
              longEdgeSize: Int,
              visiblePartSize: Int,
              edgeWithArrowSize: Int) : super(graph, printElementManager) {
    myEdgesInRowGenerator = EdgesInRowGenerator(graph)
    myGraphElementComparator = printElementManager.graphElementComparator
    myLongEdgeSize = longEdgeSize
    myVisiblePartSize = visiblePartSize
    myEdgeWithArrowSize = edgeWithArrowSize
  }

  fun getRecommendedWidth(): Int {
    if (myRecommendedWidth <= 0) {
      val n = Math.min(SAMPLE_SIZE, myLinearGraph.nodesCount())

      var sum = 0.0
      var sumSquares = 0.0
      var edgesCount = 0
      val currentNormalEdges = ContainerUtil.newHashSet<NormalEdge>()

      for (i in 0 until n) {
        val adjacentEdges = myLinearGraph.getAdjacentEdges(i, EdgeFilter.ALL)
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
            if (arrow === AbstractPrintElementGenerator.RowElementType.DOWN_ARROW) {
              downArrows++
            }
            else if (arrow === AbstractPrintElementGenerator.RowElementType.UP_ARROW) {
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
      myRecommendedWidth = Math.round(average + deviation).toInt()
    }

    return myRecommendedWidth
  }

  override fun getDownShortEdges(rowIndex: Int): List<AbstractPrintElementGenerator.ShortEdge> {
    val endPosition = createEndPositionFunction(rowIndex)

    val result = ArrayList<AbstractPrintElementGenerator.ShortEdge>()
    val visibleElements = getSortedVisibleElementsInRow(rowIndex)

    for (startPosition in visibleElements.indices) {
      val element = visibleElements[startPosition]
      if (element is GraphNode) {
        val nodeIndex = element.nodeIndex
        for (edge in myLinearGraph.getAdjacentEdges(nodeIndex, EdgeFilter.ALL)) {
          if (isEdgeDown(edge, nodeIndex)) {
            val endPos = endPosition(edge)
            if (endPos != null) result.add(AbstractPrintElementGenerator.ShortEdge(edge, startPosition, endPos))
          }
        }
      }

      if (element is GraphEdge) {
        val endPos = endPosition(element)
        if (endPos != null) result.add(AbstractPrintElementGenerator.ShortEdge(element, startPosition, endPos))
      }
    }

    return result
  }

  private fun createEndPositionFunction(visibleRowIndex: Int): (GraphEdge) -> Int? {
    val visibleElementsInNextRow = getSortedVisibleElementsInRow(visibleRowIndex + 1)

    val toPosition = HashMap<GraphElement, Int>()
    for (position in visibleElementsInNextRow.indices) {
      toPosition.put(visibleElementsInNextRow[position], position)
    }

    return { edge ->
      var position: Int? = toPosition[edge]
      if (position == null) {
        val downNodeIndex = edge.getDownNodeIndex()
        if (downNodeIndex != null) position = toPosition[myLinearGraph.getGraphNode(downNodeIndex!!)]
      }
      position
    }
  }

  override fun getSimpleRowElements(rowIndex: Int): List<AbstractPrintElementGenerator.SimpleRowElement> {
    val result = SmartList<AbstractPrintElementGenerator.SimpleRowElement>()
    val sortedVisibleElementsInRow = getSortedVisibleElementsInRow(rowIndex)

    for (position in sortedVisibleElementsInRow.indices) {
      val element = sortedVisibleElementsInRow[position]
      if (element is GraphNode) {
        result.add(AbstractPrintElementGenerator.SimpleRowElement(element, AbstractPrintElementGenerator.RowElementType.NODE, position))
      }

      if (element is GraphEdge) {
        val arrowType = getArrowType(element, rowIndex)
        if (arrowType != null) {
          result.add(AbstractPrintElementGenerator.SimpleRowElement(element, arrowType, position))
        }
      }
    }
    return result
  }

  private fun getArrowType(edge: GraphEdge, rowIndex: Int): AbstractPrintElementGenerator.RowElementType? {
    val normalEdge = asNormalEdge(edge)
    if (normalEdge != null) {
      return getArrowType(normalEdge, rowIndex)
    }
    else { // special edges
      when (edge.type) {
        GraphEdgeType.DOTTED_ARROW_DOWN, GraphEdgeType.NOT_LOAD_COMMIT -> if (intEqual(edge.upNodeIndex, rowIndex - 1)) {
          return AbstractPrintElementGenerator.RowElementType.DOWN_ARROW
        }
        GraphEdgeType.DOTTED_ARROW_UP ->
          // todo case 0-row arrow
          if (intEqual(edge.downNodeIndex, rowIndex + 1)) {
            return AbstractPrintElementGenerator.RowElementType.UP_ARROW
          }
        else -> LOG.error("Unknown special edge type " + edge.type + " at row " + rowIndex)
      }
    }
    return null
  }

  private fun getArrowType(normalEdge: NormalEdge, rowIndex: Int): AbstractPrintElementGenerator.RowElementType? {
    val edgeSize = normalEdge.down - normalEdge.up
    val upOffset = rowIndex - normalEdge.up
    val downOffset = normalEdge.down - rowIndex

    if (edgeSize >= myLongEdgeSize) {
      if (upOffset == myVisiblePartSize) {
        LOG.assertTrue(downOffset != myVisiblePartSize, "Both up and down arrow at row " + rowIndex) // this can not happen due to how constants are picked out, but just in case
        return AbstractPrintElementGenerator.RowElementType.DOWN_ARROW
      }
      if (downOffset == myVisiblePartSize) return AbstractPrintElementGenerator.RowElementType.UP_ARROW
    }
    if (edgeSize >= myEdgeWithArrowSize) {
      if (upOffset == 1) {
        LOG.assertTrue(downOffset != 1, "Both up and down arrow at row " + rowIndex)
        return AbstractPrintElementGenerator.RowElementType.DOWN_ARROW
      }
      if (downOffset == 1) return AbstractPrintElementGenerator.RowElementType.UP_ARROW
    }
    return null
  }

  private fun isEdgeVisibleInRow(edge: GraphEdge, visibleRowIndex: Int): Boolean {
    val normalEdge = asNormalEdge(edge) ?: // e.d. edge is special. See addSpecialEdges
        return false
    return isEdgeVisibleInRow(normalEdge, visibleRowIndex)
  }

  private fun isEdgeVisibleInRow(normalEdge: NormalEdge, visibleRowIndex: Int): Boolean {
    return normalEdge.down - normalEdge.up < myLongEdgeSize || getAttachmentDistance(normalEdge, visibleRowIndex) <= myVisiblePartSize
  }

  private fun addSpecialEdges(result: MutableList<GraphElement>, rowIndex: Int) {
    if (rowIndex > 0) {
      for (edge in myLinearGraph.getAdjacentEdges(rowIndex - 1, EdgeFilter.SPECIAL)) {
        assert(!edge.type.isNormalEdge)
        if (isEdgeDown(edge, rowIndex - 1)) result.add(edge)
      }
    }
    if (rowIndex < myLinearGraph.nodesCount() - 1) {
      for (edge in myLinearGraph.getAdjacentEdges(rowIndex + 1, EdgeFilter.SPECIAL)) {
        assert(!edge.type.isNormalEdge)
        if (isEdgeUp(edge, rowIndex + 1)) result.add(edge)
      }
    }
  }

  private fun getSortedVisibleElementsInRow(rowIndex: Int): List<GraphElement> {
    val graphElements = myCache.get(rowIndex)
    if (graphElements != null) {
      return graphElements
    }

    val result = ArrayList<GraphElement>()
    result.add(myLinearGraph.getGraphNode(rowIndex))

    for (edge in myEdgesInRowGenerator.getEdgesInRow(rowIndex)) {
      if (isEdgeVisibleInRow(edge, rowIndex)) result.add(edge)
    }

    addSpecialEdges(result, rowIndex)

    Collections.sort(result, myGraphElementComparator)
    myCache.put(rowIndex, result)
    return result
  }

  companion object {
    private val LOG = Logger.getInstance(PrintElementGeneratorImpl::class.java)

    private val VERY_LONG_EDGE_SIZE = 1000
    @JvmField val LONG_EDGE_SIZE = 30
    private val VERY_LONG_EDGE_PART_SIZE = 250
    private val LONG_EDGE_PART_SIZE = 1

    private val CACHE_SIZE = 100
    private val SHOW_ARROW_WHEN_SHOW_LONG_EDGES = true
    private val SAMPLE_SIZE = 20000
    private val K = 0.1

    private fun getAttachmentDistance(e1: NormalEdge, rowIndex: Int): Int {
      return Math.min(rowIndex - e1.up, e1.down - rowIndex)
    }
  }
}
