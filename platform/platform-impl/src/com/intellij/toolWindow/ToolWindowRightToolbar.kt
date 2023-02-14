// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.util.ui.JBUI

internal class ToolWindowRightToolbar(paneId: String) : ToolWindowToolbar() {
  override val topStripe = StripeV2(this, paneId, ToolWindowAnchor.RIGHT)
  override val bottomStripe = StripeV2(this, paneId, ToolWindowAnchor.BOTTOM, split = true)

  init {
    init()
  }
  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.RIGHT -> topStripe
      ToolWindowAnchor.BOTTOM -> bottomStripe
      else -> throw IllegalArgumentException("Wrong anchor $anchor")
    }
  }

  override fun createBorder() = JBUI.Borders.customLineLeft(getBorderColor())
}