// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.print.elements

import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.printer.GraphPrintElement
import com.intellij.vcs.log.graph.api.printer.PrintElementPresentationManager

internal abstract class PrintElementBase(override val graphElement: GraphElement,
                                         private val presentationManager: PrintElementPresentationManager) : GraphPrintElement {
  override val colorId get() = presentationManager.getColorId(graphElement)
  override val isSelected get() = presentationManager.isSelected(this)
}