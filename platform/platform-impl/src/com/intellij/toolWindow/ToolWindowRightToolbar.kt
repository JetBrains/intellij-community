// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.ui.UIBundle
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBInsets
import java.awt.Component
import java.awt.Insets
import javax.swing.border.Border

internal class ToolWindowRightToolbar(paneId: String, isPrimary: Boolean) : ToolWindowToolbar(isPrimary, ToolWindowAnchor.RIGHT) {
  override val topStripe: StripeV2 = StripeV2(this, paneId, ToolWindowAnchor.RIGHT)
  override val bottomStripe: StripeV2 = StripeV2(this, paneId, ToolWindowAnchor.BOTTOM, split = true)
  override val moreButton: MoreSquareStripeButton = MoreSquareStripeButton(this, ToolWindowAnchor.RIGHT, ToolWindowAnchor.LEFT)
  override val accessibleGroupName: String get() = UIBundle.message("toolbar.group.right.accessible.group.name")

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

  override fun createBorder(): Border {
    return object : CustomLineBorder(getBorderColor(), JBInsets(0, 1, 0, 0)) {
      override fun getBorderInsets(c: Component?): Insets {
        return if (hasButtons()) super.getBorderInsets(c) else JBInsets.emptyInsets()
      }
    }
  }
}