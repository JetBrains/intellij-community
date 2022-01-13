// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingConstants

internal class ToolWindowPaneOldButtonManager : ToolWindowButtonManager {
  private val leftStripe = Stripe(SwingConstants.LEFT)
  private val rightStripe = Stripe(SwingConstants.RIGHT)
  private val bottomStripe = Stripe(SwingConstants.BOTTOM)
  private val topStripe = Stripe(SwingConstants.TOP)

  private val stripes = java.util.List.of(topStripe, leftStripe, bottomStripe, rightStripe)

  override fun add(pane: JComponent) {
  }

  override fun addToToolWindowPane(pane: JComponent) {
    pane.add(topStripe, JLayeredPane.POPUP_LAYER)
    pane.add(leftStripe, JLayeredPane.POPUP_LAYER)
    pane.add(bottomStripe, JLayeredPane.POPUP_LAYER)
    pane.add(rightStripe, JLayeredPane.POPUP_LAYER)
  }

  override fun updateToolStripesVisibility(showButtons: Boolean, state: ToolWindowPaneState): Boolean {
    val oldVisible = leftStripe.isVisible
    val visible = showButtons || state.isStripesOverlaid

    leftStripe.isVisible = visible
    rightStripe.isVisible = visible
    topStripe.isVisible = visible
    bottomStripe.isVisible = visible

    val overlaid = !showButtons && state.isStripesOverlaid

    leftStripe.setOverlaid(overlaid)
    rightStripe.setOverlaid(overlaid)
    topStripe.setOverlaid(overlaid)
    bottomStripe.setOverlaid(overlaid)

    return oldVisible != visible
  }

  override fun layout(size: Dimension, layeredPane: JComponent) {
    val topSize = topStripe.preferredSize
    val bottomSize = bottomStripe.preferredSize
    val leftSize = leftStripe.preferredSize
    val rightSize = rightStripe.preferredSize
    val topBounds = Rectangle(0, 0, size.width, topSize!!.height)
    val height = size.height - topSize.height - bottomSize!!.height
    val leftBounds = Rectangle(0, topSize.height, leftSize!!.width, height)
    val rightBounds = Rectangle(size.width - rightSize!!.width, topSize.height, rightSize.width, height)
    val bottomBounds = Rectangle(0, size.height - bottomSize.height, size.width, bottomSize.height)
    topStripe.putClientProperty(Stripe.VIRTUAL_BOUNDS, topBounds)
    leftStripe.putClientProperty(Stripe.VIRTUAL_BOUNDS, leftBounds)
    rightStripe.putClientProperty(Stripe.VIRTUAL_BOUNDS, rightBounds)
    bottomStripe.putClientProperty(Stripe.VIRTUAL_BOUNDS, bottomBounds)
    if (topStripe.isVisible) {
      topStripe.bounds = topBounds
      leftStripe.bounds = leftBounds
      rightStripe.bounds = rightBounds
      bottomStripe.bounds = bottomBounds
      val uiSettings = UISettings.instance
      if (uiSettings.hideToolStripes || uiSettings.presentationMode) {
        layeredPane.setBounds(0, 0, size.width, size.height)
      }
      else {
        val width = size.width - leftSize.width - rightSize.width
        layeredPane.setBounds(leftSize.width, topSize.height, width, height)
      }
    }
    else {
      topStripe.setBounds(0, 0, 0, 0)
      bottomStripe.setBounds(0, 0, 0, 0)
      leftStripe.setBounds(0, 0, 0, 0)
      rightStripe.setBounds(0, 0, 0, 0)
      layeredPane.setBounds(0, 0, size.width, size.height)
    }
  }

  override fun validateAndRepaint() {
    for (stripe in stripes) {
      stripe.revalidate()
      stripe.repaint()
    }
  }

  override fun revalidateNotEmptyStripes() {
    for (stripe in stripes) {
      if (!stripe.isEmpty) {
        stripe.revalidate()
      }
    }
  }

  override fun getBottomHeight() = if (bottomStripe.isVisible) bottomStripe.height else 0

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when(anchor) {
      ToolWindowAnchor.TOP -> topStripe
      ToolWindowAnchor.BOTTOM -> bottomStripe
      ToolWindowAnchor.LEFT -> leftStripe
      ToolWindowAnchor.RIGHT -> rightStripe
      else -> throw IllegalArgumentException("Anchor=$anchor")
    }
  }

  override fun getStripeFor(screenPoint: Point, preferred: AbstractDroppableStripe, pane: JComponent): AbstractDroppableStripe? {
    if (Rectangle(pane.locationOnScreen, pane.size).contains(screenPoint)) {
      if (preferred.containsPoint(screenPoint)) {
        return preferred
      }
      return stripes.firstOrNull { it.containsPoint(screenPoint)  }
    }
    return null
  }

  override fun startDrag() {
    for (s in stripes) {
      if (s.isVisible) {
        s.startDrag()
      }
    }
  }

  override fun stopDrag() {
    for (s in stripes) {
      if (s.isVisible) {
        s.stopDrag()
      }
    }
  }

  override fun reset() {
    stripes.forEach(Stripe::reset)
  }

  override fun onStripeButtonAdded(toolWindow: ToolWindowImpl) {
  }

  override fun onStripeButtonRemoved(toolWindow: ToolWindowImpl) {
  }

  override fun onStripeButtonUpdate(toolWindow: ToolWindow, property: ToolWindowProperty, entry: ToolWindowEntry?) {
    val stripeButton = entry?.stripeButton
    if (stripeButton != null) {
      if (property == ToolWindowProperty.ICON) {
        stripeButton.updateIcon(toolWindow.icon)
      }
      else {
        stripeButton.updatePresentation()
      }
    }
  }
}