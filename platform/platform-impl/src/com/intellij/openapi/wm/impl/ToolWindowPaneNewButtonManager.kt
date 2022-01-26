// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent

internal class ToolWindowPaneNewButtonManager : ToolWindowButtonManager {
  private val left = ToolwindowLeftToolbar()
  private val right = ToolwindowRightToolbar()

  override fun add(pane: JComponent) {
    pane.add(left, BorderLayout.WEST)
    pane.add(right, BorderLayout.EAST)
  }

  override fun updateToolStripesVisibility(showButtons: Boolean, state: ToolWindowPaneState): Boolean {
    val oldSquareVisible = left.isVisible && right.isVisible
    left.isVisible = showButtons
    right.isVisible = showButtons
    return oldSquareVisible != showButtons
  }

  override fun layout(size: Dimension, layeredPane: JComponent) {
    layeredPane.setBounds(0, 0, size.width, size.height)
  }

  override fun validateAndRepaint() {
  }

  override fun revalidateNotEmptyStripes() {
  }

  override fun getBottomHeight() = 0

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.LEFT, ToolWindowAnchor.BOTTOM -> left.getStripeFor(anchor)
      ToolWindowAnchor.RIGHT, ToolWindowAnchor.TOP -> right.getStripeFor(anchor)
      else -> throw IllegalArgumentException("Anchor=$anchor")
    }
  }

  override fun getStripeFor(screenPoint: Point, preferred: AbstractDroppableStripe, pane: JComponent): AbstractDroppableStripe? {
    if (preferred.containsPoint(screenPoint)) {
      return preferred
    }
    else {
      return left.getStripeFor(screenPoint) ?: right.getStripeFor(screenPoint)
    }
  }

  fun getSquareStripeFor(anchor: ToolWindowAnchor): ToolwindowToolbar {
    return when(anchor) {
      ToolWindowAnchor.TOP, ToolWindowAnchor.RIGHT -> right
      ToolWindowAnchor.BOTTOM, ToolWindowAnchor.LEFT -> left
      else -> throw java.lang.IllegalArgumentException("Anchor=$anchor")
    }
  }

  override fun startDrag() {
    if (right.isVisible) {
      right.startDrag()
    }
    if (left.isVisible) {
      left.startDrag()
    }
  }

  override fun stopDrag() {
    if (right.isVisible) {
      right.stopDrag()
    }
    if (left.isVisible) {
      left.stopDrag()
    }
  }

  override fun reset() {
    left.reset()
    right.reset()
  }

  private fun findToolbar(anchor: ToolWindowAnchor): ToolwindowToolbar? {
    when(anchor) {
      ToolWindowAnchor.LEFT, ToolWindowAnchor.BOTTOM -> return left
      ToolWindowAnchor.RIGHT -> return right
      else -> return null
    }
  }

  override fun onStripeButtonAdded(toolWindow: ToolWindowImpl) {
    findToolbar(toolWindow.largeStripeAnchor)?.addStripeButton(toolWindow)
  }

  override fun onStripeButtonRemoved(toolWindow: ToolWindowImpl) {
    if (!toolWindow.isAvailable) {
      return
    }

    val anchor = toolWindow.largeStripeAnchor
    findToolbar(anchor)?.removeStripeButton(toolWindow, anchor)
  }

  override fun onStripeButtonUpdate(toolWindow: ToolWindow, property: ToolWindowProperty, entry: ToolWindowEntry?) {
    if (property == ToolWindowProperty.ICON) {
      findToolbar(toolWindow.largeStripeAnchor)?.getButtonFor(toolWindow.id)?.updateIcon(toolWindow)
    }
  }
}