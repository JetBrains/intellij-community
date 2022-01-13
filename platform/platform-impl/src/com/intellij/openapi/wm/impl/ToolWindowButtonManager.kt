// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent

internal interface ToolWindowButtonManager {
  fun add(pane: JComponent)

  fun addToToolWindowPane(pane: JComponent) {
  }

  fun updateToolStripesVisibility(showButtons: Boolean, state: ToolWindowPaneState): Boolean

  fun layout(size: Dimension, layeredPane: JComponent)

  fun validateAndRepaint()

  fun revalidateNotEmptyStripes()

  fun getBottomHeight(): Int

  fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe

  fun getStripeFor(screenPoint: Point, preferred: AbstractDroppableStripe, pane: JComponent): AbstractDroppableStripe?

  fun startDrag()

  fun stopDrag()

  fun reset()

  fun onStripeButtonAdded(toolWindow: ToolWindowImpl)

  fun onStripeButtonRemoved(toolWindow: ToolWindowImpl)

  fun onStripeButtonUpdate(toolWindow: ToolWindow, property: ToolWindowProperty, entry: ToolWindowEntry?)
}