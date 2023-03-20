// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.awt.DevicePoint
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent

internal class ToolWindowPaneNewButtonManager(paneId: String, isPrimary: Boolean) : ToolWindowButtonManager {

  constructor(paneId: String) : this(paneId, true)

  private val left = ToolWindowLeftToolbar(paneId, isPrimary)
  private val right = ToolWindowRightToolbar(paneId)

  override val isNewUi: Boolean
    get() = true

  override fun add(pane: JComponent) {
    pane.add(left, BorderLayout.WEST)
    pane.add(right, BorderLayout.EAST)
  }

  override fun updateToolStripesVisibility(showButtons: Boolean, state: ToolWindowPaneState): Boolean {
    val oldSquareVisible = left.isVisible && right.isVisible
    val visible = showButtons || state.isStripesOverlaid
    left.isVisible = visible
    right.isVisible = visible
    return oldSquareVisible != visible
  }

  override fun initMoreButton() {
    left.initMoreButton()
  }

  override fun layout(size: Dimension, layeredPane: JComponent) {
    layeredPane.setBounds(0, 0, size.width, size.height)
  }

  override fun validateAndRepaint() {
  }

  override fun revalidateNotEmptyStripes() {
  }

  override fun getBottomHeight() = 0

  override fun getStripeFor(anchor: ToolWindowAnchor, isSplit: Boolean?): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.LEFT -> left.getStripeFor(anchor)
      ToolWindowAnchor.BOTTOM -> isSplit?.let { if (it) right.getStripeFor(anchor) else left.getStripeFor(anchor) }
                                 ?: throw IllegalArgumentException("Split mode isn't expected to be used here, anchor: " + anchor.displayName)
      ToolWindowAnchor.RIGHT -> right.getStripeFor(anchor)
      else -> throw IllegalArgumentException("Anchor=$anchor")
    }
  }

  override fun getStripeFor(devicePoint: DevicePoint, preferred: AbstractDroppableStripe, pane: JComponent): AbstractDroppableStripe? {
    val screenPoint = devicePoint.getLocationOnScreen(pane)
    return if (preferred.containsPoint(screenPoint)) {
      preferred
    }
    else {
      left.getStripeFor(screenPoint) ?: right.getStripeFor(screenPoint)
    }
  }

  override fun getStripeWidth(anchor: ToolWindowAnchor): Int {
    if (anchor == ToolWindowAnchor.BOTTOM || anchor == ToolWindowAnchor.TOP) return 0
    val stripe = getStripeFor(anchor, null)
    return if (stripe.isVisible && stripe.isShowing) stripe.width else 0
  }

  override fun getStripeHeight(anchor: ToolWindowAnchor): Int {
    // New UI only shows stripes on the LEFT + RIGHT. There is no TOP, and while BOTTOM is used, it is shown on the left, so has no height
    return 0
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

  fun refreshUi() {
    left.repaint()
    right.repaint()
  }

  private fun findToolbar(anchor: ToolWindowAnchor, isSplit: Boolean) =
    when (anchor) {
      ToolWindowAnchor.LEFT -> left
      ToolWindowAnchor.BOTTOM -> if (isSplit) right else left
      ToolWindowAnchor.RIGHT -> right
      else -> left
    }

  override fun createStripeButton(toolWindow: ToolWindowImpl, info: WindowInfo, task: RegisterToolWindowTask?): StripeButtonManager {
    val squareStripeButton = SquareStripeButton(toolWindow)
    val manager = object : StripeButtonManager {
      override val id: String = toolWindow.id
      override val toolWindow: ToolWindowImpl = toolWindow

      override val windowDescriptor: WindowInfo
        get() = toolWindow.windowInfo

      override fun updateState(toolWindow: ToolWindowImpl) {
        squareStripeButton.updateIcon()
      }

      override fun updatePresentation() {
        squareStripeButton.updatePresentation()
      }

      override fun updateIcon(icon: Icon?) {
        squareStripeButton.updatePresentation()
      }

      override fun remove(anchor: ToolWindowAnchor, split: Boolean) {
        findToolbar(anchor, split).getStripeFor(anchor).removeButton(this)
      }

      override fun getComponent() = squareStripeButton

      override fun toString(): String {
        return "SquareStripeButtonManager(windowInfo=${toolWindow.windowInfo})"
      }
    }
    findToolbar(toolWindow.anchor, toolWindow.isSplitMode).getStripeFor(toolWindow.windowInfo.anchor).addButton(manager)
    return manager
  }

  override fun hasButtons() = left.hasButtons() || right.hasButtons()
}