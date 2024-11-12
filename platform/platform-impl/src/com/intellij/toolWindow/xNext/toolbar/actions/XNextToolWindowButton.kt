// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.toolbar.actions

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.wm.impl.AbstractSquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.toolWindow.ToolWindowDragHelper
import com.intellij.ui.MouseDragHelper
import java.awt.Dimension
import java.util.function.Supplier

internal class XNextToolWindowButton(action: XNextToolWindowButtonAction, override val toolWindow: ToolWindowImpl, presentation: Presentation,
                            minimumSize: Supplier<Dimension>? = null) :
  AbstractSquareStripeButton(action, presentation, minimumSize), ToolWindowDragHelper.ToolWindowProvider  {

  init {
    doInit { createPopupGroup() }
    MouseDragHelper.setComponentDraggable(this, true)
  }

  private fun createPopupGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()
    /*
    group.add(TogglePinActionBase(toolWindowId))
    group.addSeparator()
    */
    group.add(SquareStripeButton.createMoveGroup())
    return group
  }
}