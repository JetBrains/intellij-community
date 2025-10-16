// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.DisclosureButton
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import javax.swing.JComponent
import javax.swing.SwingConstants

object WelcomeScreenVerticalToolbar {
  fun createToolbar(group: ActionGroup): ActionToolbar {
    val toolbar = ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, LeftPanelActionGroupWrapper(group), false, false, false)
    toolbar.isOpaque = false
    toolbar.isReservePlaceAutoPopupIcon = false
    toolbar.layoutStrategy = VerticalToolbarLayoutStrategy()

    toolbar.targetComponent = toolbar

    // + JBInsets(3) from DarculaDisclosureButtonBorder
    toolbar.border = JBUI.Borders.empty(/* top = */ 5, /* left = */ 17, /* bottom = */ 0, /* right = */ 17)

    return toolbar
  }
}

private class LeftPanelActionGroupWrapper(group: ActionGroup) : ActionGroupWrapper(group) {
  override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
    visibleChildren.forEach { action ->
      e.updateSession.presentation(action).putClientProperty(ActionUtil.COMPONENT_PROVIDER,
                                                             LeftPanelDisclosureButtonAction(action))
    }
    return visibleChildren
  }
}

private class VerticalToolbarLayoutStrategy : ToolbarLayoutStrategy {
  private val unscaledVerticalGap = 2 // + 3 * 2 = 8, from DarculaDisclosureButtonBorder

  override fun calculateBounds(toolbar: ActionToolbar): List<Rectangle> {
    val res = mutableListOf<Rectangle>()

    val bounds = toolbar.component.bounds
    val insets = toolbar.component.insets

    var yOffset = 0
    for (child in toolbar.component.components) {
      val d = if (child.isVisible) child.preferredSize else Dimension()
      res.add(Rectangle(insets.left, insets.top + yOffset, bounds.width - insets.left - insets.right, d.height))
      yOffset += d.height + JBUI.scale(unscaledVerticalGap)
    }

    return res;
  }

  override fun calcPreferredSize(toolbar: ActionToolbar): Dimension {
    var width = 0
    var height = 0

    for (component in toolbar.component.components.filter { it.isVisible }) {
      val preferredSize = component.preferredSize
      width = maxOf(width, preferredSize.width)
      height += preferredSize.height + JBUI.scale(unscaledVerticalGap)
    }
    if (height > 0) height -= JBUI.scale(unscaledVerticalGap)
    val result = JBUI.size(width, height)
    JBInsets.addTo(result, toolbar.component.insets)
    return result
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension = calcPreferredSize(toolbar)
}

private class LeftPanelDisclosureButtonAction(private val actionDelegate: AnAction) : CustomComponentAction {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = DisclosureButton()
    button.arrowIcon = null

    button.addActionListener { performAction(button, presentation) }

    updateCustomComponent(button, presentation)

    return button
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    if (component !is DisclosureButton) return

    component.text = presentation.text
    component.icon = presentation.icon ?: EmptyIcon.ICON_16

    val inlineActions = presentation.getClientProperty(ActionUtil.INLINE_ACTIONS).orEmpty()
    if (inlineActions.isNotEmpty()) {
      component.additionalAction = object : DisclosureButton.ActionListener {
        override fun actionTriggered(e: InputEvent?) {
          showInlineActionsPopup(component, presentation, inlineActions)
        }
      }
    }
    else {
      component.additionalAction = null
    }
    UIUtil.setEnabled(component, presentation.isEnabled, true)
  }

  private fun performAction(component: JComponent, presentation: Presentation) {
    val dataContext = ActionToolbar.getDataContextFor(component)
    val ev = AnActionEvent.createEvent(actionDelegate, dataContext, presentation, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null)
    ActionUtil.invokeAction(actionDelegate, ev, null)
  }

  private fun showInlineActionsPopup(component: JComponent, presentation: Presentation, inlineActions: List<AnAction>) {
    val dataContext = ActionToolbar.getDataContextFor(component)
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(null,
                                                                    DefaultActionGroup(inlineActions),
                                                                    dataContext,
                                                                    JBPopupFactory.ActionSelectionAid.MNEMONICS,
                                                                    false,
                                                                    ActionPlaces.WELCOME_SCREEN)

    val adText = presentation.getClientProperty(WelcomeScreenActionsUtil.INLINE_ACTIONS_POPUP_AD_TEXT) // NON-NLS
    if (adText != null) popup.setAdText(adText, SwingConstants.LEFT)

    val targetPoint = RelativePoint(component, Point(component.width + JBUI.scale(4), 0))
    popup.show(targetPoint)
  }
}
