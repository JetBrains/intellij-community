// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.LayoutData
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel

internal class ToolWindowLeftToolbar : ToolWindowToolbar() {
  private class StripeV2(private val toolBar: ToolWindowLeftToolbar,
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

  private val topLeftStripe = StripeV2(this, ToolWindowAnchor.LEFT)
  private val bottomLeftStripe = StripeV2(this, ToolWindowAnchor.BOTTOM)

  val moreButton = MoreSquareStripeButton(this)

  init {
    val topWrapper = JPanel(BorderLayout())
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 0, 0, 1)
    topLeftStripe.background = JBUI.CurrentTheme.ToolWindow.background()
    bottomLeftStripe.background = JBUI.CurrentTheme.ToolWindow.background()
    topWrapper.background = JBUI.CurrentTheme.ToolWindow.background()

    topWrapper.add(topLeftStripe, BorderLayout.NORTH)
    add(topWrapper, BorderLayout.NORTH)
    add(bottomLeftStripe, BorderLayout.SOUTH)
  }

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.LEFT -> topLeftStripe
      ToolWindowAnchor.BOTTOM -> bottomLeftStripe
      else -> throw IllegalArgumentException("Wrong anchor $anchor")
    }
  }

  fun initMoreButton() {
    topLeftStripe.parent?.add(moreButton, BorderLayout.CENTER)
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isVisible || !moreButton.isVisible) {
      return null
    }

    val moreButtonRect = Rectangle(moreButton.locationOnScreen, moreButton.size)
    return if (Rectangle(topLeftStripe.locationOnScreen, topLeftStripe.size).contains(screenPoint) ||
               topLeftStripe.getButtons().isEmpty() && moreButtonRect.contains(screenPoint)) {
      topLeftStripe
    }
    else if (!moreButtonRect.contains(screenPoint) &&
             Rectangle(locationOnScreen, size).also { JBInsets.removeFrom(it, insets) }.contains(screenPoint)) {
      bottomLeftStripe
    }
    else {
      null
    }
  }

  override fun reset() {
    topLeftStripe.reset()
    bottomLeftStripe.reset()
  }

  override fun getButtonFor(toolWindowId: String): StripeButtonManager? {
    return topLeftStripe.getButtons().find { it.id == toolWindowId } ?: bottomLeftStripe.getButtons().find { it.id == toolWindowId }
  }
}