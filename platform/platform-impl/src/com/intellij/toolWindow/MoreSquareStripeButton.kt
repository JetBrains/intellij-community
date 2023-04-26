// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon

internal class MoreSquareStripeButton(toolWindowToolbar: ToolWindowLeftToolbar) :
  ActionButton(createAction(toolWindowToolbar), createPresentation(), ActionPlaces.TOOLWINDOW_TOOLBAR_BAR,
               { JBUI.CurrentTheme.Toolbar.stripeToolbarButtonSize() }) {

  init {
    setLook(SquareStripeButtonLook(this))
  }

  override fun updateToolTipText() {
    HelpTooltip()
      .setTitle(UIBundle.message("tool.window.new.stripe.more.title"))
      .setLocation(HelpTooltip.Alignment.RIGHT)
      .setInitialDelay(0).setHideDelay(0)
      .installOn(this)
  }

  override fun checkSkipPressForEvent(e: MouseEvent) = e.button != MouseEvent.BUTTON1

  override fun updateUI() {
    super.updateUI()
    myPresentation.icon = scaleIcon()
  }
}

private fun createPresentation(): Presentation {
  val presentation = Presentation()
  presentation.icon = scaleIcon()
  presentation.isEnabledAndVisible = true
  return presentation
}

private fun scaleIcon(): Icon {
  return loadIconCustomVersionOrScale(
    icon = AllIcons.Actions.MoreHorizontal as ScalableIcon,
    size = JBUI.CurrentTheme.Toolbar.stripeToolbarButtonIconSize()
  )
}

private fun createAction(toolWindowToolbar: ToolWindowLeftToolbar): DumbAwareAction {
  return object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val actions = ToolWindowsGroup.getToolWindowActions(e.project ?: return, true)
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, DefaultActionGroup(actions), e.dataContext, null, true)
      popup.setMinimumSize(Dimension(300, -1))

      val moreSquareStripeButton = toolWindowToolbar.moreButton
      popup.show(RelativePoint(toolWindowToolbar, Point(toolWindowToolbar.width, moreSquareStripeButton.y)))
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = ToolWindowsGroup.getToolWindowActions(e.project ?: return, true).isNotEmpty()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
  }
}
