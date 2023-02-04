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
import java.util.*

internal class PrintElementPresentationManagerImpl<CommitId>(permanentGraphInfo: PermanentGraphInfo<CommitId>,
                                                             private val linearGraph: LinearGraph,
                                                             colorManager: GraphColorManager<CommitId>) : PrintElementPresentationManager {
  private val colorGetter = ColorGetterByLayoutIndex(linearGraph, permanentGraphInfo, colorManager)
  private var selection: Selection = Selection(linearGraph, emptySet())

  override fun isSelected(printElement: PrintElementWithGraphElement): Boolean {
    return selection.isSelected(printElement)
  }

  fun setSelectedElement(printElement: PrintElementWithGraphElement): Boolean {
    return setSelection(Selection(linearGraph, printElement))
  }

  fun setSelectedElements(selectedNodeId: Set<Int>): Boolean {
    return setSelection(Selection(linearGraph, selectedNodeId))
  }

  private fun setSelection(newSelection: Selection): Boolean {
    if (newSelection == selection) return false
    selection = newSelection
    return true
  }

  override fun getColorId(element: GraphElement): Int {
    return colorGetter.getColorId(element)
  }

  class Selection private constructor(private val linearGraph: LinearGraph,
                                      private val selectedPrintElement: PrintElementWithGraphElement?,
                                      private val selectedNodeIds: Set<Int>) {
    constructor(linearGraph: LinearGraph, selectedNodeId: Set<Int>) : this(linearGraph, null, selectedNodeId)
    constructor(linearGraph: LinearGraph, printElement: PrintElementWithGraphElement) : this(linearGraph, printElement, emptySet<Int>())

    fun isSelected(printElement: PrintElementWithGraphElement): Boolean {
      if (printElement == selectedPrintElement) return true

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
      if (other == null || javaClass != other.javaClass) return false
      val selection = other as Selection
      return selectedPrintElement == selection.selectedPrintElement &&
             Comparing.haveEqualElements(selectedNodeIds, selection.selectedNodeIds)
    }

    override fun hashCode(): Int {
      return 31 * Objects.hashCode(selectedPrintElement) + Comparing.unorderedHashcode(selectedNodeIds)
    }
  }
}
