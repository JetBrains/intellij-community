// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import com.intellij.ui.popup.PopupFactoryImpl.InlineActionItem
import com.intellij.ui.popup.list.ListPopupImpl.ListWithInlineButtons
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import java.awt.event.InputEvent
import javax.swing.*

class PopupInlineActionsSupportImpl(private val myListPopup: ListPopupImpl) : PopupInlineActionsSupport {

  private val myStep = myListPopup.listStep as ActionPopupStep

  override fun hasExtraButtons(element: Any): Boolean = calcExtraButtonsCount(element) > 0

  override fun calcExtraButtonsCount(element: Any): Int {
    if (!ExperimentalUI.isNewUI() || element !is ActionItem) return 0

    var res = 0
    res += myStep.getInlineActions(element).size
    if (myStep.hasSubstep(element)) res++
    return res
  }

  override fun calcButtonIndex(element: Any?, point: Point): Int? {
    if (element == null) return null
    val list = myListPopup.list
    val index = list.selectedIndex
    val bounds = list.getCellBounds(index, index) ?: return null
    JBInsets.removeFrom(bounds, PopupListElementRenderer.getListCellPadding())

    val buttonsCount: Int = calcExtraButtonsCount(element)
    if (buttonsCount <= 0) return null

    val distanceToRight = bounds.x + bounds.width - point.x
    val buttonsToRight = distanceToRight / (PopupListElementRenderer.INLINE_BUTTON_WIDTH + PopupListElementRenderer.INLINE_BUTTONS_GAP)
    if (buttonsToRight >= buttonsCount) return null

    val distanceToNearest = distanceToRight - buttonsToRight * (PopupListElementRenderer.INLINE_BUTTON_WIDTH + PopupListElementRenderer.INLINE_BUTTONS_GAP)
    return if (distanceToNearest > PopupListElementRenderer.INLINE_BUTTON_WIDTH) null else buttonsCount - buttonsToRight - 1
  }

  override fun runInlineAction(element: Any, index: Int, event: InputEvent?) = getExtraButtonsActions(element, event)[index].run()

  private fun getExtraButtonsActions(element: Any, event: InputEvent?): List<Runnable> {
    if (!ExperimentalUI.isNewUI() || element !is ActionItem) return emptyList()

    val res: MutableList<Runnable> = ArrayList()

    res.addAll(myStep.getInlineActions(element).map {
      item: InlineActionItem -> createInlineActionRunnable(item.action, event)
    })
    if (myStep.hasSubstep(element)) res.add(createNextStepRunnable(element))
    return res
  }

  override fun getExtraButtons(list: JList<*>, value: Any, isSelected: Boolean): List<JComponent> {
    if (value !is ActionItem) return emptyList()
    val inlineActions = myStep.getInlineActions(value)
    if (inlineActions.isEmpty()) return emptyList()

    val res: MutableList<JComponent> = java.util.ArrayList()
    val activeIndex = getActiveButtonIndex(list)

    for (i in 0 until inlineActions.size) res.add(createActionButton(inlineActions[i], i == activeIndex, isSelected))
    res.add(createSubmenuButton(value, res.size == activeIndex))

    return res
  }

  private fun getActiveButtonIndex(list: JList<*>): Int? = (list as? ListWithInlineButtons)?.selectedButtonIndex

  private fun createSubmenuButton(value: ActionItem, active: Boolean): JComponent {
    val icon = if (myStep.isFinal(value)) AllIcons.Actions.More else AllIcons.Icons.Ide.MenuArrow
    return createExtraButton(icon, active)
  }


  private fun createActionButton(action: InlineActionItem, active: Boolean, isSelected: Boolean): JComponent =
    createExtraButton(action.getIcon(isSelected), active)

  private fun createExtraButton(icon: Icon, active: Boolean): JComponent {
    val label = JLabel(icon)
    val panel = JPanel(BorderLayout())
    panel.add(label)
    val size = panel.preferredSize
    size.width = PopupListElementRenderer.INLINE_BUTTON_WIDTH
    panel.preferredSize = size
    panel.minimumSize = size
    panel.isOpaque = active
    panel.background = JBUI.CurrentTheme.Table.Hover.background(true)
    return panel
  }

  private fun createNextStepRunnable(element: ActionItem) =
    Runnable { myListPopup.showNextStepPopup(myStep.onChosen(element, false), element) }

  private fun createInlineActionRunnable(action: AnAction, inputEvent: InputEvent?) = Runnable {
    myStep.performAction(action, inputEvent)
  }
}