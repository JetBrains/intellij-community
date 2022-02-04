// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.ToolWindowImpl
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import javax.swing.Icon
import javax.swing.JComponent

internal class ToolWindowPaneNewButtonManager : ToolWindowButtonManager {
  private val left = ToolWindowLeftToolbar()
  private val right = ToolWindowRightToolbar()

  override val isNewUi: Boolean
    get() = true

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

  fun getSquareStripeFor(anchor: ToolWindowAnchor): ToolWindowToolbar {
    return when (anchor) {
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

  fun refreshUI() {
    left.repaint()
    right.repaint()
  }

  private fun findToolbar(anchor: ToolWindowAnchor): ToolWindowToolbar = if (anchor == ToolWindowAnchor.RIGHT) right else left

  override fun createStripeButton(toolWindow: ToolWindowImpl, info: WindowInfo, task: RegisterToolWindowTask?): StripeButtonManager {
    val squareStripeButton = SquareStripeButton(toolWindow)
    val manager = object : StripeButtonManager {
      override val id: String
        get() = toolWindow.id

      override val windowDescriptor: WindowInfo
        get() = toolWindow.windowInfo

      override fun updateState(toolWindow: ToolWindowImpl) {
        squareStripeButton.updateIcon()
      }

      override fun updatePresentation() {
        squareStripeButton.syncIcon()
      }

      override fun updateIcon(icon: Icon?) {
        squareStripeButton.syncIcon()
      }

      override fun remove() {
        findToolbar(toolWindow.anchor).getStripeFor(toolWindow.windowInfo.anchor).removeButton(this)
      }

      override fun getComponent() = squareStripeButton

      override fun toString(): String {
        return "SquareStripeButtonManager(windowInfo=${toolWindow.windowInfo})"
      }
    }
    findToolbar(toolWindow.anchor).getStripeFor(toolWindow.windowInfo.anchor).addButton(manager)
    return manager
  }
}