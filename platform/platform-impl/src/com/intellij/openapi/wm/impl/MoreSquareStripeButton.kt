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
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Point
import java.util.function.Predicate

class MoreSquareStripeButton(toolwindowSideBar: ToolwindowToolbar, val stripe: AbstractDroppableStripe) :
  ActionButton(createAction(toolwindowSideBar), createPresentation(), ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, Dimension(40, 40)) {

  init {
    setLook(SquareStripeButtonLook(this))
  }

  override fun updateToolTipText() {
    HelpTooltip().
      setTitle(UIBundle.message("title.tool.window.square.more")).
      setLocation(HelpTooltip.Alignment.RIGHT).
      setInitialDelay(0).setHideDelay(0).
      installOn(this)
  }

  companion object {
    val largeStripeToolwindowPredicate: Predicate<ToolWindow> = Predicate { !it.isVisibleOnLargeStripe }

    fun createPresentation(): Presentation {
      return Presentation().apply {
        icon = AllIcons.Actions.MoreHorizontal
        isEnabledAndVisible = true
      }
    }

    fun createAction(toolwindowSideBar: ToolwindowToolbar): DumbAwareAction =
      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
          val moreSquareStripeButton = UIUtil.findComponentOfType(toolwindowSideBar, MoreSquareStripeButton::class.java)
          ToolwindowSwitcher.invokePopup(e.project!!, Comparator.comparing { toolwindow: ToolWindow -> toolwindow.stripeTitle },
                                         largeStripeToolwindowPredicate,
                                         RelativePoint(toolwindowSideBar, Point(toolwindowSideBar.width, moreSquareStripeButton?.y ?: 0)))
        }

        override fun update(e: AnActionEvent) {
          e.project?.let {
            e.presentation.isEnabledAndVisible = ToolwindowSwitcher.getToolWindows(it, largeStripeToolwindowPredicate).isNotEmpty()
          }
        }
      }
  }
}