// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ToggleActionButton
import java.awt.Dimension

class MoreSquareStripeButton(toolwindowSideBar: IdeLeftToolbar) :
  ActionButton(createAction(toolwindowSideBar), createPresentation(), ActionPlaces.TOOLWINDOW_SIDE_BAR, Dimension(40, 40)) {

  companion object {
    fun createPresentation(): Presentation {
      return Presentation().apply {
        icon = AllIcons.Actions.More
        isEnabledAndVisible = true
      }
    }

    fun createAction(toolwindowSideBar: IdeLeftToolbar): ToggleActionButton =
      object : ToggleActionButton(Presentation.NULL_STRING, null), DumbAware {
        override fun isSelected(e: AnActionEvent?) = toolwindowSideBar.isExtendedToolwindowPaneShown()
        override fun setSelected(e: AnActionEvent?, state: Boolean) = toolwindowSideBar.openExtendedToolwindowPane(state)
      }
  }
}