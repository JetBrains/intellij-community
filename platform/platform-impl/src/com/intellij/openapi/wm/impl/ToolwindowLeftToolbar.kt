// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.toolWindow.StripeButtonManager
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

internal class ToolwindowLeftToolbar : ToolwindowToolbar() {
  private class StripeV2(private val toolBar: ToolwindowLeftToolbar,
                         override val anchor: ToolWindowAnchor) : AbstractDroppableStripe(VerticalFlowLayout(0, 0)) {
    override val isNewStripes: Boolean
      get() = true

    override fun getButtonFor(toolWindowId: String) = toolBar.getButtonFor(toolWindowId)

    override fun getToolWindowFor(component: JComponent) = (component as SquareStripeButton).toolWindow

    override fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) {
      toolBar.tryDroppingOnGap(data, gap, dropRectangle) {
        layoutDragButton(data, gap)
      }
    }

    override fun toString() = "StripeNewUi(anchor=$anchor)"
  }

  private val topPane = StripeV2(this, ToolWindowAnchor.LEFT)
  private val bottomPane = StripeV2(this, ToolWindowAnchor.BOTTOM)

  val moreButton = MoreSquareStripeButton(this)

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 1)
    topPane.background = JBUI.CurrentTheme.ToolWindow.background()
    bottomPane.background = JBUI.CurrentTheme.ToolWindow.background()

    val topWrapper = JPanel(BorderLayout())
    topWrapper.add(topPane, BorderLayout.NORTH)
    topWrapper.add(moreButton, BorderLayout.CENTER)
    add(topWrapper, BorderLayout.NORTH)
    add(bottomPane, BorderLayout.SOUTH)
  }

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.LEFT -> topPane
      ToolWindowAnchor.BOTTOM -> bottomPane
      ToolWindowAnchor.TOP -> bottomPane
      else -> throw IllegalArgumentException("Wrong anchor $anchor")
    }
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isVisible || !moreButton.isVisible) {
      return null
    }

    val moreButtonRect = Rectangle(moreButton.locationOnScreen, moreButton.size)
    if (Rectangle(topPane.locationOnScreen, topPane.size).contains(screenPoint) ||
        topPane.getButtons().isEmpty() && moreButtonRect.contains(screenPoint)) {
      return topPane
    }
    else if (!moreButtonRect.contains(screenPoint) &&
             Rectangle(locationOnScreen, size).also { JBInsets.removeFrom(it, insets) }.contains(screenPoint)) {
      return bottomPane
    }
    else {
      return null
    }
  }

  override fun reset() {
    topPane.reset()
    bottomPane.reset()
  }

  override fun getButtonFor(toolWindowId: String): StripeButtonManager? {
    return topPane.getButtons().find { it.id == toolWindowId } ?: bottomPane.getButtons().find { it.id == toolWindowId }
  }
}