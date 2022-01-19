// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ToolwindowSwitcher
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import java.awt.Dimension
import java.awt.Point
import java.util.function.Predicate

internal class MoreSquareStripeButton(toolWindowToolbar: ToolwindowLeftToolbar) :
  ActionButton(createAction(toolWindowToolbar), createPresentation(), ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, Dimension(40, 40)) {

  init {
    setLook(SquareStripeButtonLook(this))
  }

  override fun updateToolTipText() {
    HelpTooltip()
      .setTitle(UIBundle.message("title.tool.window.square.more"))
      .setLocation(HelpTooltip.Alignment.RIGHT)
      .setInitialDelay(0).setHideDelay(0)
      .installOn(this)
  }

  companion object {
    private val notVisibleOnStripePredicate: Predicate<ToolWindow> = Predicate { !it.isShowStripeButton }

    private fun createPresentation(): Presentation {
      val presentation = Presentation()
      presentation.icon = IconLoader.loadCustomVersionOrScale(AllIcons.Actions.MoreHorizontal as ScalableIcon, 20f)
      presentation.isEnabledAndVisible = true
      return presentation
    }

    private fun createAction(toolWindowToolbar: ToolwindowLeftToolbar): DumbAwareAction {
      return object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
          val moreSquareStripeButton = toolWindowToolbar.moreButton
          ToolwindowSwitcher.invokePopup(e.project!!, Comparator.comparing { it.stripeTitle },
                                         notVisibleOnStripePredicate,
                                         RelativePoint(toolWindowToolbar, Point(toolWindowToolbar.width, moreSquareStripeButton.y)))
        }

        override fun update(e: AnActionEvent) {
          val toolWindowManager = ToolWindowManagerEx.getInstanceEx(e.project ?: return)
          e.presentation.isEnabledAndVisible = ToolwindowSwitcher.getToolWindows(toolWindowManager, notVisibleOnStripePredicate).isNotEmpty()
        }
      }
    }
  }
}