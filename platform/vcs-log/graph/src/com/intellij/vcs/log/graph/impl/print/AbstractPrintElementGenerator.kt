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

import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator
import com.intellij.vcs.log.graph.api.printer.PrintElementManager
import com.intellij.vcs.log.graph.impl.print.elements.EdgePrintElementImpl
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement
import com.intellij.vcs.log.graph.impl.print.elements.SimplePrintElementImpl
import com.intellij.vcs.log.graph.impl.print.elements.TerminalEdgePrintElement
import java.lang.IllegalStateException

import java.util.ArrayList

abstract class AbstractPrintElementGenerator protected constructor(protected val myLinearGraph: LinearGraph, protected val myPrintElementManager: PrintElementManager) : PrintElementGenerator {

  override fun getPrintElements(rowIndex: Int): Collection<PrintElementWithGraphElement> {
    val result = ArrayList<PrintElementWithGraphElement>()

    val simpleRowElements = getSimpleRowElements(rowIndex)

    val arrows = ContainerUtil.newHashMap<GraphEdge, SimpleRowElement>()
    for (rowElement in simpleRowElements) {
      if (rowElement.myType != RowElementType.NODE) {
        arrows.put(rowElement.myElement as GraphEdge, rowElement)
      }
    }

    if (rowIndex < myLinearGraph.nodesCount() - 1) {
      for (shortEdge in getDownShortEdges(rowIndex)) {
        var rowElementType = RowElementType.NODE
        if (arrows[shortEdge.myEdge] != null && RowElementType.DOWN_ARROW == arrows[shortEdge.myEdge]!!.myType) {
          rowElementType = RowElementType.DOWN_ARROW
          arrows.remove(shortEdge.myEdge)
        }
        result.add(createEdgePrintElement(rowIndex, shortEdge, EdgePrintElement.Type.DOWN, rowElementType != RowElementType.NODE))
      }
    }

    if (rowIndex > 0) {
      for (shortEdge in getDownShortEdges(rowIndex - 1)) {
        var rowElementType = RowElementType.NODE
        if (arrows[shortEdge.myEdge] != null && RowElementType.UP_ARROW == arrows[shortEdge.myEdge]!!.myType) {
          rowElementType = RowElementType.UP_ARROW
          arrows.remove(shortEdge.myEdge)
        }
        result.add(createEdgePrintElement(rowIndex, shortEdge, EdgePrintElement.Type.UP, rowElementType != RowElementType.NODE))
      }
    }

    for (arrow in arrows.values) {
      result.add(TerminalEdgePrintElement(rowIndex, arrow.myPosition, if (arrow.myType == RowElementType.UP_ARROW)
        EdgePrintElement.Type.UP
      else
        EdgePrintElement.Type.DOWN, arrow.myElement as GraphEdge,
          myPrintElementManager))
    }

    for (rowElement in simpleRowElements) {
      if (rowElement.myType == RowElementType.NODE) {
        result.add(createSimplePrintElement(rowIndex, rowElement))
      }
    }

    return result
  }

  private fun createSimplePrintElement(rowIndex: Int, rowElement: SimpleRowElement): SimplePrintElementImpl {
    return SimplePrintElementImpl(rowIndex, rowElement.myPosition, rowElement.myElement, myPrintElementManager)
  }

  private fun createEdgePrintElement(rowIndex: Int,
                                     shortEdge: ShortEdge,
                                     type: EdgePrintElement.Type,
                                     hasArrow: Boolean): EdgePrintElementImpl {
    val positionInCurrentRow: Int
    val positionInOtherRow: Int
    if (type == EdgePrintElement.Type.DOWN) {
      positionInCurrentRow = shortEdge.myUpPosition
      positionInOtherRow = shortEdge.myDownPosition
    }
    else {
      positionInCurrentRow = shortEdge.myDownPosition
      positionInOtherRow = shortEdge.myUpPosition
    }
    return EdgePrintElementImpl(rowIndex, positionInCurrentRow, positionInOtherRow, type, shortEdge.myEdge, hasArrow,
        myPrintElementManager)
  }

  override fun withGraphElement(printElement: PrintElement): PrintElementWithGraphElement {
    if (printElement is PrintElementWithGraphElement) {
      return printElement
    }

    val rowIndex = printElement.rowIndex
    for (printElementWithGE in getPrintElements(rowIndex)) {
      if (printElementWithGE == printElement) return printElementWithGE
    }
    throw IllegalStateException("Not found graphElement for this printElement: " + printElement)
  }

  // rowIndex in [0, getCountVisibleRow() - 2]
  protected abstract fun getDownShortEdges(rowIndex: Int): Collection<ShortEdge>

  protected abstract fun getSimpleRowElements(rowIndex: Int): Collection<SimpleRowElement>

  protected class ShortEdge(val myEdge: GraphEdge, val myUpPosition: Int, val myDownPosition: Int)

  protected class SimpleRowElement(val myElement: GraphElement, val myType: RowElementType, val myPosition: Int)

  protected enum class RowElementType {
    NODE,
    UP_ARROW,
    DOWN_ARROW
  }
}
