// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.LayoutData
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.Rectangle

internal class ToolWindowRightToolbar(paneId: String) : ToolWindowToolbar() {
  private val rightStripe = object : AbstractDroppableStripe(paneId, VerticalFlowLayout(0, 0)) {
    override val isNewStripes = true
    override val anchor = ToolWindowAnchor.RIGHT

    override fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) {
      tryDroppingOnGap(data, gap, dropRectangle) { layoutDragButton(data, gap) }
    }

    override fun getButtonFor(toolWindowId: String) = this@ToolWindowRightToolbar.getButtonFor(toolWindowId)
  }

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 1, 0, 0)
    rightStripe.background = JBUI.CurrentTheme.ToolWindow.background()
    add(rightStripe)
  }

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.RIGHT -> rightStripe
      else -> throw IllegalArgumentException("Wrong anchor $anchor")
    }
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isShowing) {
      return null
    }

    val toolBarRect = Rectangle(rightStripe.locationOnScreen, rightStripe.size).also {
      if (it.width == 0) {
        it.width = SHADOW_WIDTH
        it.x -= SHADOW_WIDTH
      }
    }
    return if (toolBarRect.contains(screenPoint)) rightStripe else null
  }

  override fun reset() {
    rightStripe.reset()
  }

  override fun getButtonFor(toolWindowId: String): StripeButtonManager? = rightStripe.getButtons().find { it.id == toolWindowId }

  override fun hasButtons() = rightStripe.getButtons().isNotEmpty()
}