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

internal class ToolWindowLeftToolbar(paneId: String, private val isPrimary: Boolean) : ToolWindowToolbar() {
  private class StripeV2(private val toolBar: ToolWindowLeftToolbar,
                         paneId: String,
                         override val anchor: ToolWindowAnchor) : AbstractDroppableStripe(paneId, VerticalFlowLayout(0, 0)) {
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

  private val topLeftStripe = StripeV2(this, paneId,  ToolWindowAnchor.LEFT)
  private val bottomLeftStripe = StripeV2(this, paneId, ToolWindowAnchor.BOTTOM)

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
    if (isPrimary) topLeftStripe.parent?.add(moreButton, BorderLayout.CENTER)
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isVisible) {
      return null
    }

    if (isPrimary) {
      // We have a more button, so the stripe is always visible, and always has a size
      val moreButtonRect = Rectangle(moreButton.locationOnScreen, moreButton.size)
      return if (Rectangle(topLeftStripe.locationOnScreen, topLeftStripe.size).contains(screenPoint)
                 || moreButtonRect.contains(screenPoint)) {
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
    else {
      // Note that this has different behaviour to when we have a more button. In that case, we treat the more button as a separator.
      // Anything above the separator is part of the top/left stripe, anything below is part of the bottom/left stripe. But without the more
      // button, we don't have that separator, so it feels more natural to try and dock to the top/left by default. We also give the
      // bottom/left stripe a bit of extra space to make it easier to drop onto the top position
      val topLeftRect = Rectangle(topLeftStripe.locationOnScreen, topLeftStripe.size).also {
        if (it.width == 0) it.width = SHADOW_WIDTH
        it.height = height - maxOf(SHADOW_WIDTH, bottomLeftStripe.height + SHADOW_WIDTH)
      }
      return if (topLeftRect.contains(screenPoint)) {
        topLeftStripe
      }
      else if (Rectangle(locationOnScreen, size).also {
          it.y -= SHADOW_WIDTH
          it.height += SHADOW_WIDTH
        }.contains(screenPoint)) {
        bottomLeftStripe
      }
      else {
        null
      }
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