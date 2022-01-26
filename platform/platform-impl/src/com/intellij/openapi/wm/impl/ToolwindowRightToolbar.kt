// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.toolWindow.StripeButtonManager
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

internal class ToolwindowRightToolbar : ToolwindowToolbar() {
  val topPane = object : AbstractDroppableStripe(VerticalFlowLayout(0, 0)) {
    override val isNewStripes: Boolean
      get() = true
    override val anchor: ToolWindowAnchor
      get() = ToolWindowAnchor.RIGHT

    override fun getToolWindowFor(component: JComponent) = (component as SquareStripeButton).toolWindow

    override fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) {
      tryDroppingOnGap(data, gap, dropRectangle) { layoutDragButton(data, gap) }
    }

    override fun getButtonFor(toolWindowId: String) = this@ToolwindowRightToolbar.getButtonFor(toolWindowId)
  }

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 1, 0, 0)
    topPane.background = JBUI.CurrentTheme.ToolWindow.background()
    add(topPane)
  }

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.RIGHT -> topPane
      else -> throw IllegalArgumentException("Wrong anchor $anchor")
    }
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isVisible) {
      return null
    }

    val toolBarRect = Rectangle(topPane.locationOnScreen, topPane.size).also {
      if (it.width == 0) {
        it.width = SHADOW_WIDTH
        it.x -= SHADOW_WIDTH
      }
    }
    return if (toolBarRect.contains(screenPoint)) topPane else null
  }

  override fun reset() {
    topPane.removeAll()
    topPane.revalidate()
  }

  override fun getButtonFor(toolWindowId: String): StripeButtonManager? = topPane.getButtons().find { it.id == toolWindowId }

  companion object {
    val SHADOW_WIDTH = JBUI.scale(40)
  }
}