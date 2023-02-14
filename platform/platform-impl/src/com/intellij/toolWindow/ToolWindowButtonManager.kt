// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.toolWindow

import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.awt.DevicePoint
import java.awt.Dimension
import javax.swing.JComponent

internal interface ToolWindowButtonManager {
  val isNewUi: Boolean

  fun add(pane: JComponent)

  fun addToToolWindowPane(pane: JComponent) {
  }

  fun initMoreButton() {}

  fun updateToolStripesVisibility(showButtons: Boolean, state: ToolWindowPaneState): Boolean

  fun layout(size: Dimension, layeredPane: JComponent)

  fun validateAndRepaint()

  fun revalidateNotEmptyStripes()

  fun getBottomHeight(): Int

  fun getStripeFor(anchor: ToolWindowAnchor, isSplit: Boolean?): AbstractDroppableStripe

  fun getStripeFor(devicePoint: DevicePoint, preferred: AbstractDroppableStripe, pane: JComponent): AbstractDroppableStripe?

  fun getStripeWidth(anchor: ToolWindowAnchor): Int
  fun getStripeHeight(anchor: ToolWindowAnchor): Int

  fun startDrag()

  fun stopDrag()

  fun reset()

  fun createStripeButton(toolWindow: ToolWindowImpl, info: WindowInfo, task: RegisterToolWindowTask?): StripeButtonManager

  fun hasButtons(): Boolean
}