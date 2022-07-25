// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle

internal class ToolWindowLeftToolbar(paneId: String, private val isPrimary: Boolean) : ToolWindowToolbar() {
  override val topStripe = StripeV2(this, paneId, ToolWindowAnchor.LEFT)
  override val bottomStripe = StripeV2(this, paneId, ToolWindowAnchor.BOTTOM)

  init {
    init()
  }

  val moreButton = MoreSquareStripeButton(this)

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.LEFT -> topStripe
      ToolWindowAnchor.BOTTOM -> bottomStripe
      else -> throw IllegalArgumentException("Wrong anchor $anchor")
    }
  }

  override fun createBorder() = JBUI.Borders.customLine(getBorderColor(), 1, 0, 0, 1)

  fun initMoreButton() {
    if (isPrimary) topStripe.parent?.add(moreButton, BorderLayout.CENTER)
  }

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isShowing) {
      return null
    }

    if (!isPrimary) {
      return super.getStripeFor(screenPoint)
    }

    // We have a more button, so the stripe is always visible, and always has a size
    val moreButtonRect = Rectangle(moreButton.locationOnScreen, moreButton.size)
    return if (Rectangle(topStripe.locationOnScreen, topStripe.size).contains(screenPoint)
               || moreButtonRect.contains(screenPoint)) {
      topStripe
    }
    else if (!moreButtonRect.contains(screenPoint) &&
             Rectangle(locationOnScreen, size).also { JBInsets.removeFrom(it, insets) }.contains(screenPoint)) {
      bottomStripe
    }
    else {
      null
    }
  }
}