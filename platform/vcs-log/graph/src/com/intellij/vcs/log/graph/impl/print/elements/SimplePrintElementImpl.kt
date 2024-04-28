// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print.elements

import com.intellij.vcs.log.graph.NodePrintElement
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager

class SimplePrintElementImpl(rowIndex: Int, positionInCurrentRow: Int, graphElement: GraphElement,
                             presentationManager: PrintElementPresentationManager) : PrintElementWithGraphElement(rowIndex,
                                                                                                                  positionInCurrentRow,
                                                                                                                  graphElement,
                                                                                                                  presentationManager), NodePrintElement {
  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o !is NodePrintElement) return false

    if (positionInCurrentRow != o.positionInCurrentRow) return false
    if (rowIndex != o.rowIndex) return false

    return true
  }

  override fun hashCode(): Int {
    var result = rowIndex
    result = 31 * result + positionInCurrentRow
    return result
  }
}
