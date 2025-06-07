// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.toolWindow.MoreSquareStripeButton
import com.intellij.toolWindow.ToolWindowToolbar
import org.jetbrains.annotations.ApiStatus
import java.awt.FlowLayout

@ApiStatus.Internal
class XNextToolWindowToolbar(paneId: String, isPrimary: Boolean) : ToolWindowToolbar(isPrimary, ToolWindowAnchor.BOTTOM)  {

  override val topStripe: StripeV2 = StripeV2(this, paneId, ToolWindowAnchor.LEFT, layout = FlowLayout(0))
  override val bottomStripe: StripeV2 = StripeV2(this, paneId, ToolWindowAnchor.RIGHT, layout = FlowLayout(0))

  override val moreButton: MoreSquareStripeButton = MoreSquareStripeButton(this, ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT)
  override val accessibleGroupName: String get() = "XNext Toolbar"

  init {
    init()
  }

  override fun init() {
    super.init()
    layout = FlowLayout(FlowLayout.LEFT, 0, 0)
  }



  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe {
    return topStripe
  }


/*  internal class XNextStripe() : StripeV2() {

  }*/
}

