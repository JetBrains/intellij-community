// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print.elements

import com.intellij.vcs.log.graph.PrintElement
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager

abstract class PrintElementWithGraphElement protected constructor(override val rowIndex: Int,
                                                                  override val positionInCurrentRow: Int,
                                                                  val graphElement: GraphElement,
                                                                  protected val presentationManager: PrintElementPresentationManager) : PrintElement {
  override val colorId get() = presentationManager.getColorId(graphElement)
  override val isSelected get() = presentationManager.isSelected(this)

  companion object {
    fun converted(element: PrintElementWithGraphElement, convertedGraphElement: GraphElement): PrintElementWithGraphElement {
      return object : PrintElementWithGraphElement(element.rowIndex, element.positionInCurrentRow, convertedGraphElement,
                                                   element.presentationManager) {
      }
    }
  }
}
