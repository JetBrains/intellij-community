// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

class ToolwindowLeftToolbar : ToolwindowToolbar() {
  private val topPane = object : AbstractDroppableStripe(VerticalFlowLayout(0, 0)) {
    override fun getAnchor(): ToolWindowAnchor = ToolWindowAnchor.LEFT
    override fun getButtonFor(toolWindowId: String): JComponent? = this@ToolwindowLeftToolbar.getButtonFor(toolWindowId)
    override fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) =
      tryDroppingOnGap(data, gap, myDropRectangle) { layoutDragButton(data, gap) }
  }

  private val bottomPane = object : AbstractDroppableStripe(VerticalFlowLayout(0, 0)) {
    override fun getAnchor(): ToolWindowAnchor = ToolWindowAnchor.BOTTOM
    override fun getButtonFor(toolWindowId: String): JComponent? = this@ToolwindowLeftToolbar.getButtonFor(toolWindowId)
  }

  val moreButton = MoreSquareStripeButton(this, topPane)

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

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe = when (anchor) {
    ToolWindowAnchor.LEFT -> topPane
    ToolWindowAnchor.BOTTOM -> bottomPane
    else -> throw IllegalArgumentException("Wrong anchor $anchor")
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? = if (isVisible && moreButton.isVisible) {
    val moreButtonRect = Rectangle(moreButton.locationOnScreen, moreButton.size)
    if (Rectangle(topPane.locationOnScreen, topPane.size).contains(screenPoint) ||
        topPane.buttons.isEmpty() && moreButtonRect.contains(screenPoint)) topPane
    else if (!moreButtonRect.contains(screenPoint) &&
             Rectangle(locationOnScreen, size).also{ JBInsets.removeFrom(it, insets)}.contains(screenPoint)) bottomPane
    else null
  }
  else null

  override fun reset() {
    topPane.removeAll()
    topPane.revalidate()
    bottomPane.removeAll()
    bottomPane.revalidate()
  }

  override fun getButtonFor(toolWindowId: String): SquareStripeButton? =
    topPane.components.filterIsInstance(SquareStripeButton::class.java).find {it.button.id == toolWindowId} ?:
     bottomPane.components.filterIsInstance(SquareStripeButton::class.java).find {it.button.id == toolWindowId}
}