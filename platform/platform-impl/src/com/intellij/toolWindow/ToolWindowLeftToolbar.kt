// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.border.Border

internal class ToolWindowLeftToolbar(paneId: String, private val isPrimary: Boolean) : ToolWindowToolbar() {
  override val topStripe = StripeV2(this, paneId, ToolWindowAnchor.LEFT)
  override val bottomStripe = StripeV2(this, paneId, ToolWindowAnchor.BOTTOM)

  init {
    init()
  }

  val moreButton: MoreSquareStripeButton = MoreSquareStripeButton(this)

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.LEFT -> topStripe
      ToolWindowAnchor.BOTTOM -> bottomStripe
      else -> throw IllegalArgumentException("Wrong anchor $anchor")
    }
  }

  override fun createBorder(): Border = JBUI.Borders.customLine(getBorderColor(), 1, 0, 0, 1)

  fun initMoreButton() {
    if (isPrimary) {
      topStripe.parent?.add(moreButton, BorderLayout.CENTER)
    }
  }
}