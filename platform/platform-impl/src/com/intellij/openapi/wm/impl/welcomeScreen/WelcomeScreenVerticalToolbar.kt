// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.DisclosureButton
import com.intellij.util.ui.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.InputEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent
import javax.swing.SwingConstants


@ApiStatus.Internal
fun createFrameWelcomeScreenVerticalToolbar(
  group: ActionGroup,
  disposable: Disposable,
): ActionToolbar {
  val type = WelcomeScreenToolbarType.FRAME
  val groupWrapper = WelcomeScreenActionGroupWrapper(group, type, disposable)
  val toolbar = createWelcomeScreenVerticalToolbar(groupWrapper, type, requestFocus = true)

  toolbar.component.border = JBUI.Borders.empty(5)
  return toolbar
}

@ApiStatus.Internal
fun createToolWindowWelcomeScreenVerticalToolbar(
  group: ActionGroup,
): ActionToolbar {
  val type = WelcomeScreenToolbarType.TOOLWINDOW
  val groupWrapper = WelcomeScreenActionGroupWrapper(group, type, null)
  val toolbar = createWelcomeScreenVerticalToolbar(groupWrapper, type, requestFocus = false)

  // + JBInsets(3) from DarculaDisclosureButtonBorder
  toolbar.component.border = JBUI.Borders.empty(/* top = */ 5, /* left = */ 17, /* bottom = */ 0, /* right = */ 17)
  return toolbar
}

private fun createWelcomeScreenVerticalToolbar(
  groupWrapper: WelcomeScreenActionGroupWrapper,
  type: WelcomeScreenToolbarType,
  requestFocus: Boolean,
): ActionToolbar {
  val toolbar = WelcomeScreenVerticalToolbar(ActionPlaces.WELCOME_SCREEN, groupWrapper, requestFocus = requestFocus)
  toolbar.isOpaque = false
  toolbar.isReservePlaceAutoPopupIcon = false
  toolbar.layoutStrategy = VerticalToolbarLayoutStrategy(type)

  toolbar.targetComponent = toolbar

  return toolbar
}

private class WelcomeScreenVerticalToolbar(place: String, actionGroup: ActionGroup, requestFocus: Boolean)
  : ActionToolbarImpl(place, actionGroup, false, false, false) {
  private var shouldRequestFocus = requestFocus
  override fun isSecondaryAction(action: AnAction, actionIndex: Int): Boolean {
    return actionIndex >= getWelcomeScreenPrimaryButtonsNum()
  }
  override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
    super.actionsUpdated(forced, newVisibleActions)
    if (forced && !newVisibleActions.isEmpty() && componentCount > 0 && shouldRequestFocus) {
      val obj = FocusUtil.findFocusableComponentIn(components[0], null)
      if (obj != null) {
        shouldRequestFocus = false
        IdeFocusManager.getGlobalInstance().requestFocus(obj, true)
      }
    }
  }
}

internal class WelcomeScreenActionGroupWrapper(
  group: ActionGroup,
  val type: WelcomeScreenToolbarType,
  val sliderDisposable: Disposable?,
) : ActionGroupWrapper(group) {
  private val wrappers = ConcurrentHashMap<AnAction, AnAction>()

  override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
    val mappedChildren = visibleChildren
      .map { action ->
        when {
          action is ActionGroup && action is ActionsWithPanelProvider && sliderDisposable != null -> {
            val wrapper = wrappers.getOrPut(action) {
              ActionGroupPanelWrapper.wrapGroups(action, sliderDisposable)
            }
            e.updateSession.presentation(wrapper)
            wrapper
          }
          else -> action
        }
      }

    mappedChildren.forEach { action ->
      e.updateSession.presentation(action).putClientProperty(
        ActionUtil.COMPONENT_PROVIDER,
        WelcomeScreenDisclosureButtonAction(action, type)
      )
    }
    return mappedChildren
  }
}

private class VerticalToolbarLayoutStrategy(
  private val type: WelcomeScreenToolbarType,
) : ToolbarLayoutStrategy {
  private val unscaledVerticalGap = when (type) {
    WelcomeScreenToolbarType.FRAME -> 6 // + 3 * 2 = 12, from DarculaDisclosureButtonBorder
    WelcomeScreenToolbarType.TOOLWINDOW -> 2 // + 3 * 2 = 8, from DarculaDisclosureButtonBorder
  }

  private val unscaledPreferredButtonWidth = when (type) {
    WelcomeScreenToolbarType.FRAME -> 278
    WelcomeScreenToolbarType.TOOLWINDOW -> 0
  }

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
    if (height > 0) {
      height -= JBUI.scale(unscaledVerticalGap) // the last button needs no gap
    }

    if (width > 0) {
      width = maxOf(width, JBUI.scale(unscaledPreferredButtonWidth))
    }

    val result = JBUI.size(width, height)
    JBInsets.addTo(result, toolbar.component.insets)
    return result
  }

  override fun calcMinimumSize(toolbar: ActionToolbar): Dimension = calcPreferredSize(toolbar)
}

private class WelcomeScreenDisclosureButtonAction(
  private val actionDelegate: AnAction,
  private val type: WelcomeScreenToolbarType,
) : CustomComponentAction {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val button = DisclosureButton()
    button.arrowIcon = null
    button.isOpaque = false

    if (type == WelcomeScreenToolbarType.FRAME) {
      JBColor.namedColorOrNull("WelcomeScreen.Frame.DisclosureButton.defaultBackground")?.let { button.defaultBackground = it }
      JBColor.namedColorOrNull("WelcomeScreen.Frame.DisclosureButton.hoverOverlay")?.let { button.hoverBackground = it }
      JBColor.namedColorOrNull("WelcomeScreen.Frame.DisclosureButton.pressedOverlay")?.let { button.pressedBackground = it }
    }

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

internal enum class WelcomeScreenToolbarType {
  FRAME, TOOLWINDOW
}
