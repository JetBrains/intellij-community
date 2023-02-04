// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.openapi.util.Comparing
import com.intellij.vcs.log.graph.GraphColorManager
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager
import com.intellij.vcs.log.graph.impl.print.ColorGetterByLayoutIndex
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement

internal class PrintElementPresentationManagerImpl<CommitId>(permanentGraphInfo: PermanentGraphInfo<CommitId>,
                                                             private val linearGraph: LinearGraph,
                                                             colorManager: GraphColorManager<CommitId>) : PrintElementPresentationManager {
  private val colorGetter = ColorGetterByLayoutIndex(linearGraph, permanentGraphInfo, colorManager)
  private var selection: Selection = Selection.FromNodeIds(linearGraph, emptySet())

  override fun isSelected(printElement: PrintElementWithGraphElement): Boolean {
    return selection.isSelected(printElement)
  }

  fun setSelectedElement(printElement: PrintElementWithGraphElement): Boolean {
    return setSelection(Selection.FromPrintElement(printElement))
  }

  fun setSelectedElements(selectedNodeId: Set<Int>): Boolean {
    return setSelection(Selection.FromNodeIds(linearGraph, selectedNodeId))
  }

  private fun setSelection(newSelection: Selection): Boolean {
    if (newSelection == selection) return false
    selection = newSelection
    return true
  }

  override fun getColorId(element: GraphElement): Int {
    return colorGetter.getColorId(element)
  }

  sealed class Selection {

    abstract fun isSelected(printElement: PrintElementWithGraphElement): Boolean

    data class FromPrintElement(private val selectedPrintElement: PrintElementWithGraphElement) : Selection() {
      override fun isSelected(printElement: PrintElementWithGraphElement): Boolean {
        return printElement == selectedPrintElement
      }
    }

    class FromNodeIds(private val linearGraph: LinearGraph, private val selectedNodeIds: Set<Int>) : Selection() {
      override fun isSelected(printElement: PrintElementWithGraphElement): Boolean {
        val graphElement = printElement.graphElement
        if (graphElement is GraphNode) {
          val nodeId = linearGraph.getNodeId(graphElement.nodeIndex)
          return selectedNodeIds.contains(nodeId)
        }
        if (graphElement is GraphEdge) {
          var selected = graphElement.targetId == null || selectedNodeIds.contains(graphElement.targetId)
          selected = selected and
            (graphElement.upNodeIndex == null || selectedNodeIds.contains(linearGraph.getNodeId(graphElement.upNodeIndex!!)))
          selected = selected and
            (graphElement.downNodeIndex == null || selectedNodeIds.contains(linearGraph.getNodeId(graphElement.downNodeIndex!!)))
          return selected
        }
        return false
      }

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FromNodeIds) return false

        return Comparing.haveEqualElements(selectedNodeIds, other.selectedNodeIds)
      }

      override fun hashCode() = Comparing.unorderedHashcode(selectedNodeIds)
    }
  }
}
