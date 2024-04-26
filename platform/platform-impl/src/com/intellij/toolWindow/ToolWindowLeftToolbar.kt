// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBUI
import javax.swing.border.Border

internal class ToolWindowLeftToolbar(paneId: String, isPrimary: Boolean) : ToolWindowToolbar(isPrimary, ToolWindowAnchor.LEFT) {
  override val topStripe: StripeV2 = StripeV2(this, paneId, ToolWindowAnchor.LEFT)
  override val bottomStripe: StripeV2 = StripeV2(this, paneId, ToolWindowAnchor.BOTTOM)
  override val moreButton: MoreSquareStripeButton = MoreSquareStripeButton(this, ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT)
  override val accessibleGroupName: String get() = UIBundle.message("toolbar.group.left.accessible.group.name")

  init {
    init()
  }

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return when (anchor) {
      ToolWindowAnchor.LEFT -> topStripe
      ToolWindowAnchor.BOTTOM -> bottomStripe
      else -> topStripe
    }
  }

  override fun createBorder(): Border = JBUI.Borders.customLineRight(getBorderColor())
}