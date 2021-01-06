// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.ToggleActionButton
import java.awt.Dimension

class SquareStripeButton(button: StripeButton) : ActionButton(createAction(button), createPresentation(button),
                                                              ActionPlaces.TOOLWINDOW_SIDE_BAR, Dimension(40, 40)) {
  companion object {
    fun createPresentation(button: StripeButton): Presentation {
      return Presentation(button.text).apply {
        icon = button.icon
        if (icon is ScalableIcon) icon = (icon as ScalableIcon).scale(1.4f)
        isEnabledAndVisible = true
      }
    }

    fun createAction(button: StripeButton): ToggleActionButton =
      object : ToggleActionButton(button.text, null), DumbAware {
        override fun isSelected(e: AnActionEvent?) = button.toolWindow.isVisible
        override fun setSelected(e: AnActionEvent?, state: Boolean) {
          val manager = button.toolWindow.toolWindowManager
          if (!state) {
            manager.hideToolWindow(button.id, false, true, ToolWindowEventSource.StripeButton)
          }
          else {
            manager.activated(button.toolWindow, ToolWindowEventSource.StripeButton)
          }
        }
      }
  }
}