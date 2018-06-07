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
package com.intellij.vcs.log.graph.collapsing

import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph.NodeFilter
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType.*
import com.intellij.vcs.log.graph.utils.LinearGraphUtils

class DottedFilterEdgesGenerator private constructor(private val collapsedGraph: CollapsedGraph,
                                                     private val modification: CollapsedGraph.Modification,
                                                     private val upIndex: Int,
                                                     private val downIndex: Int) {

  private val liteDelegateGraph: LiteLinearGraph = LinearGraphUtils.asLiteLinearGraph(collapsedGraph.delegatedGraph)
  private val numbers: ShiftNumber = ShiftNumber(upIndex, downIndex)

  private fun nodeIsVisible(nodeIndex: Int): Boolean {
    return collapsedGraph.isNodeVisible(nodeIndex)
  }

  private fun addDottedEdge(nodeIndex1: Int, nodeIndex2: Int) {
    modification.createEdge(GraphEdge(nodeIndex1, nodeIndex2, null, DOTTED))
  }

  private fun addDottedArrow(nodeIndex: Int, isUp: Boolean) {
    modification.createEdge(GraphEdge(nodeIndex, null, null, if (isUp) DOTTED_ARROW_UP else DOTTED_ARROW_DOWN))
  }

  // update specified range
  private fun update() {
    downWalk()
    cleanup()
    upWalk()
  }

  private fun cleanup() {
    for (currentNodeIndex in upIndex..downIndex) {
      numbers.setNumber(currentNodeIndex, Integer.MAX_VALUE)
    }
  }

  private fun hasDottedEdges(nodeIndex: Int, isUp: Boolean): Boolean {
    for (edge in modification.edgesToAdd.getAdjacentEdges(nodeIndex, EdgeFilter.NORMAL_ALL)) {
      if (edge.type == DOTTED) {
        if (isUp && LinearGraphUtils.isEdgeUp(edge, nodeIndex)) return true
        if (!isUp && LinearGraphUtils.isEdgeDown(edge, nodeIndex)) return false
      }
    }
    return false
  }

  private fun addEdgeOrArrow(currentNodeIndex: Int, anotherNodeIndex: Int, isUp: Boolean) {
    if (hasDottedEdges(currentNodeIndex, isUp)) {
      if (nodeIsVisible(anotherNodeIndex)) {
        addDottedEdge(currentNodeIndex, anotherNodeIndex)
      }
      else {
        addDottedArrow(currentNodeIndex, isUp)
      }
    }
  }

  private fun downWalk() {
    for (currentNodeIndex in upIndex..downIndex) {
      if (nodeIsVisible(currentNodeIndex)) {
        var nearlyUp = Integer.MIN_VALUE
        var maxAdjNumber = Integer.MIN_VALUE
        for (upNode in liteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.UP)) {
          if (upNode < upIndex) {
            addEdgeOrArrow(currentNodeIndex, upNode, true)
            continue
          }

          if (nodeIsVisible(upNode)) {
            maxAdjNumber = Math.max(maxAdjNumber, numbers.getNumber(upNode))
          }
          else {
            nearlyUp = Math.max(nearlyUp, numbers.getNumber(upNode))
          }
        }

        if (nearlyUp == maxAdjNumber || nearlyUp == Integer.MIN_VALUE) {
          numbers.setNumber(currentNodeIndex, maxAdjNumber)
        }
        else {
          addDottedEdge(currentNodeIndex, nearlyUp)
          numbers.setNumber(currentNodeIndex, nearlyUp)
        }
      }
      else {
        // node currentNodeIndex invisible

        var nearlyUp = Integer.MIN_VALUE
        for (upNode in liteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.UP)) {
          if (nodeIsVisible(upNode)) {
            nearlyUp = Math.max(nearlyUp, upNode)
          }
          else {
            if (upNode >= upIndex) nearlyUp = Math.max(nearlyUp, numbers.getNumber(upNode))
          }
        }
        numbers.setNumber(currentNodeIndex, nearlyUp)
      }
    }
  }

  private fun upWalk() {
    for (currentNodeIndex in downIndex downTo upIndex) {
      if (nodeIsVisible(currentNodeIndex)) {
        var nearlyDown = Integer.MAX_VALUE
        var minAdjNumber = Integer.MAX_VALUE
        for (downNode in liteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.DOWN)) {
          if (downNode > downIndex) {
            addEdgeOrArrow(currentNodeIndex, downNode, false)
            continue
          }

          if (nodeIsVisible(downNode)) {
            minAdjNumber = Math.min(minAdjNumber, numbers.getNumber(downNode))
          }
          else {
            nearlyDown = Math.min(nearlyDown, numbers.getNumber(downNode))
          }
        }

        if (nearlyDown == minAdjNumber || nearlyDown == Integer.MAX_VALUE) {
          numbers.setNumber(currentNodeIndex, minAdjNumber)
        }
        else {
          addDottedEdge(currentNodeIndex, nearlyDown)
          numbers.setNumber(currentNodeIndex, nearlyDown)
        }
      }
      else {
        // node currentNodeIndex invisible

        var nearlyDown = Integer.MAX_VALUE
        for (downNode in liteDelegateGraph.getNodes(currentNodeIndex, NodeFilter.DOWN)) {
          if (nodeIsVisible(downNode)) {
            nearlyDown = Math.min(nearlyDown, downNode)
          }
          else {
            if (downNode <= downIndex) nearlyDown = Math.min(nearlyDown, numbers.getNumber(downNode))
          }
        }
        numbers.setNumber(currentNodeIndex, nearlyDown)
      }
    }
  }


  internal class ShiftNumber(private val startIndex: Int, private val endIndex: Int) {
    private val numbers: IntArray = IntArray(endIndex - startIndex + 1)

    private fun inRange(nodeIndex: Int): Boolean {
      return nodeIndex in startIndex..endIndex
    }

    fun getNumber(nodeIndex: Int): Int {
      return if (inRange(nodeIndex)) numbers[nodeIndex - startIndex] else -1

    }

    fun setNumber(nodeIndex: Int, value: Int) {
      if (inRange(nodeIndex)) {
        numbers[nodeIndex - startIndex] = value
      }
    }
  }

  companion object {
    @JvmStatic
    fun update(collapsedGraph: CollapsedGraph, upDelegateNodeIndex: Int, downDelegateNodeIndex: Int) {
      val modification = collapsedGraph.startModification()
      DottedFilterEdgesGenerator(collapsedGraph, modification, upDelegateNodeIndex, downDelegateNodeIndex).update()
      modification.apply()
    }
  }
}
