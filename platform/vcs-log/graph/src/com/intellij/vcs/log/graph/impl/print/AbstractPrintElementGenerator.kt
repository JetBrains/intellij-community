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

import com.intellij.vcs.log.graph.EdgePrintElement
import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator
import com.intellij.vcs.log.graph.api.printer.PrintElementManager
import com.intellij.vcs.log.graph.impl.print.elements.EdgePrintElementImpl
import com.intellij.vcs.log.graph.impl.print.elements.PrintElementWithGraphElement
import com.intellij.vcs.log.graph.impl.print.elements.SimplePrintElementImpl
import com.intellij.vcs.log.graph.impl.print.elements.TerminalEdgePrintElement
import java.lang.IllegalStateException
import java.util.*

abstract class AbstractPrintElementGenerator protected constructor(protected val linearGraph: LinearGraph,
                                                                   private val printElementManager: PrintElementManager) : PrintElementGenerator {

  override fun getPrintElements(rowIndex: Int): Collection<PrintElementWithGraphElement> {
    val result = ArrayList<PrintElementWithGraphElement>()
    val nodes = ArrayList<PrintElementWithGraphElement>() // nodes at the end, to be drawn over the edges

    collectElements(rowIndex, object : ElementConsumer() {
      override fun consumeNode(node: GraphNode, position: Int) {
        nodes.add(createSimplePrintElement(rowIndex, SimpleRowElement(node, RowElementType.NODE, position)))
      }

      override fun consumeDownEdge(edge: GraphEdge, upPosition: Int, downPosition: Int, hasArrow: Boolean) {
        result.add(createEdgePrintElement(rowIndex, ShortEdge(edge, upPosition, downPosition), EdgePrintElement.Type.DOWN, hasArrow))
      }

      override fun consumeUpEdge(edge: GraphEdge, upPosition: Int, downPosition: Int, hasArrow: Boolean) {
        result.add(createEdgePrintElement(rowIndex, ShortEdge(edge, upPosition, downPosition), EdgePrintElement.Type.UP, hasArrow))
      }

      override fun consumeArrow(edge: GraphEdge, position: Int, arrowType: RowElementType) {
        result.add(TerminalEdgePrintElement(rowIndex, position,
                                            if (arrowType == RowElementType.UP_ARROW)
                                              EdgePrintElement.Type.UP
                                            else
                                              EdgePrintElement.Type.DOWN, edge,
                                            printElementManager))
      }
    })

    result.addAll(nodes)

    return result
  }

  private fun createSimplePrintElement(rowIndex: Int, rowElement: SimpleRowElement): SimplePrintElementImpl {
    return SimplePrintElementImpl(rowIndex, rowElement.myPosition, rowElement.myElement, printElementManager)
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
                                printElementManager)
  }

  override fun withGraphElement(printElement: PrintElement): PrintElementWithGraphElement {
    if (printElement is PrintElementWithGraphElement) {
      return printElement
    }

    return getPrintElements(printElement.rowIndex).find { it == printElement } ?:
           throw IllegalStateException("Not found graphElement for this printElement: " + printElement)
  }

  protected class ShortEdge(val myEdge: GraphEdge, val myUpPosition: Int, val myDownPosition: Int)

  protected class SimpleRowElement(val myElement: GraphElement, val myType: RowElementType, val myPosition: Int)

  protected enum class RowElementType {
    NODE,
    UP_ARROW,
    DOWN_ARROW
  }

  protected open class ElementConsumer {
    open fun consumeNode(node: GraphNode, position: Int) {}
    open fun consumeDownEdge(edge: GraphEdge, upPosition: Int, downPosition: Int, hasArrow: Boolean) {}
    open fun consumeUpEdge(edge: GraphEdge, upPosition: Int, downPosition: Int, hasArrow: Boolean) {}
    open fun consumeArrow(edge: GraphEdge, position: Int, arrowType: RowElementType) {}
  }

  protected abstract fun collectElements(rowIndex: Int, consumer: ElementConsumer)
}
