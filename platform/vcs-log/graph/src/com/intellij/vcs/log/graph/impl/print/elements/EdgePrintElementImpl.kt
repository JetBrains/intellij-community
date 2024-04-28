// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print.elements

import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphEdgeType
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager

open class EdgePrintElementImpl(rowIndex: Int, positionInCurrentRow: Int, override val positionInOtherRow: Int,
                                override val type: EdgePrintElement.Type, graphEdge: GraphEdge,
                                private val hasArrow: Boolean,
                                presentationManager: PrintElementPresentationManager) : PrintElementWithGraphElement(rowIndex,
                                                                                                                     positionInCurrentRow,
                                                                                                                     graphEdge,
                                                                                                                     presentationManager), EdgePrintElement {
  override val lineStyle = convertToLineStyle(graphEdge.type)

  override fun hasArrow(): Boolean = hasArrow

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is EdgePrintElement) return false

    if (positionInCurrentRow != o.positionInCurrentRow) return false
    if (positionInOtherRow != o.positionInOtherRow) return false
    if (rowIndex != o.rowIndex) return false
    if (type != o.type) return false
    if (hasArrow != o.hasArrow()) return false

    return true
  }

  override fun hashCode(): Int {
    var result: Int = rowIndex
    result = 31 * result + positionInCurrentRow
    result = 31 * result + positionInOtherRow
    result = 37 * result + type.hashCode()
    result = 31 * result + (if (hasArrow) 1 else 0)
    return result
  }

  companion object {
    fun convertToLineStyle(edgeType: GraphEdgeType): EdgePrintElement.LineStyle {
      return when (edgeType) {
        GraphEdgeType.USUAL, GraphEdgeType.NOT_LOAD_COMMIT -> EdgePrintElement.LineStyle.SOLID
        GraphEdgeType.DOTTED, GraphEdgeType.DOTTED_ARROW_UP, GraphEdgeType.DOTTED_ARROW_DOWN -> EdgePrintElement.LineStyle.DASHED
      }
    }
  }
}
