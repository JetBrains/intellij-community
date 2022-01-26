// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

class ToolwindowRightToolbar : ToolwindowToolbar() {
  val topPane = object : AbstractDroppableStripe(VerticalFlowLayout(0, 0)) {
    override fun getAnchor(): ToolWindowAnchor = ToolWindowAnchor.RIGHT
    override fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) =
      tryDroppingOnGap(data, gap, myDropRectangle) { layoutDragButton(data, gap) }

    override fun getButtonFor(toolWindowId: String): JComponent? = this@ToolwindowRightToolbar.getButtonFor(toolWindowId)
  }

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 1, 0, 0)
    topPane.background = JBUI.CurrentTheme.ToolWindow.background()
    add(topPane)
  }

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe = when (anchor) {
    ToolWindowAnchor.RIGHT -> topPane
    else -> throw IllegalArgumentException("Wrong anchor $anchor")
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? = if (isVisible) {
    val toolBarRect = Rectangle(topPane.locationOnScreen, topPane.size).also {
      if (it.width == 0) {
        it.width = SHADOW_WIDTH
        it.x -= SHADOW_WIDTH
      }
    }

    if (toolBarRect.contains(screenPoint)) topPane else null
  }
  else null

  override fun reset() {
    topPane.removeAll()
    topPane.revalidate()
  }

  override fun getButtonFor(toolWindowId: String): SquareStripeButton? =
    topPane.components.filterIsInstance(SquareStripeButton::class.java).find {it.button.id == toolWindowId}

  companion object {
    val SHADOW_WIDTH = JBUI.scale(40)
  }
}