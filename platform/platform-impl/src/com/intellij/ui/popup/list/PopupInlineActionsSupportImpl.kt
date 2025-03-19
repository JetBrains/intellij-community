// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl.ActionItem
import java.awt.Point
import java.awt.event.InputEvent
import javax.swing.JComponent

internal class PopupInlineActionsSupportImpl(private val myListPopup: ListPopupImpl) : PopupInlineActionsSupport {

  private val myStep = myListPopup.listStep as ActionPopupStep

  override fun calcExtraButtonsCount(element: Any?): Int {
    if (!ExperimentalUI.isNewUI() || element !is ActionItem) return 0

    var res = 0
    res += myStep.getInlineItems(element).size
    if (hasMoreButton(element)) res++
    return res
  }

  override fun calcButtonIndex(element: Any?, point: Point): Int? {
    if (element == null) return null
    val buttonsCount: Int = calcExtraButtonsCount(element)
    if (buttonsCount <= 0) return null

    return calcButtonIndex(myListPopup.list, buttonsCount, point)
  }

  override fun getToolTipText(element: Any?, index: Int): String? = when {
    element !is ActionItem -> null
    isMoreButton(element, index) -> IdeBundle.message("inline.actions.more.actions.text")
    else -> myStep.getInlineItems(element)[index]?.text
  }

  override fun getKeepPopupOnPerform(element: Any?, index: Int): KeepPopupOnPerform = when {
    element !is ActionItem -> KeepPopupOnPerform.Always
    isMoreButton(element, index) -> KeepPopupOnPerform.Always
    else -> myStep.getInlineItems(element)[index].keepPopupOnPerform
  }

  override fun performAction(element: Any?, index: Int, event: InputEvent?) = when {
    element !is ActionItem -> Unit
    isMoreButton(element, index) -> {
      myListPopup.showNextStepPopup(myStep.onChosen(element, false), element)
    }
    else -> {
      val item = myStep.getInlineItems(element)[index]
      myStep.performActionItem(item, event)
      myStep.updateStepItems(myListPopup.list)
    }
  }

  override fun createExtraButtons(value: Any?, isSelected: Boolean, activeIndex: Int): List<JComponent> {
    if (value !is ActionItem) return emptyList()
    val inlineItems = myStep.getInlineItems(value)

    val buttons = ArrayList<JComponent>()

    inlineItems.forEachIndexed { index, item ->
      if (isSelected || item.getClientProperty(ActionUtil.ALWAYS_VISIBLE_INLINE_ACTION) == true) {
        buttons.add(createActionButton(item, index == activeIndex, isSelected))
      }
    }
    if ((isSelected || buttons.isNotEmpty()) && hasMoreButton(value)) {
      val icon = when {
        myStep.isFinal(value) -> AllIcons.Actions.More
        else -> AllIcons.Icons.Ide.MenuArrow
      }
      buttons.add(createExtraButton(icon, buttons.size == activeIndex))
    }

    return buttons
  }

  private fun createActionButton(item: ActionItem, active: Boolean, isSelected: Boolean): JComponent {
    val icon = item.getIcon(isSelected)
    if (icon == null) {
      throw AssertionError("null inline item icon for action '${item.action.javaClass.name}'")
    }
    return createExtraButton(icon, active)
  }

  override fun isMoreButton(element: Any?, index: Int): Boolean {
    if (element !is ActionItem || !hasMoreButton(element)) return false
    val count = calcExtraButtonsCount(element)
    return count > 0 && index == count - 1
  }

  private fun hasMoreButton(element: ActionItem): Boolean {
    return myStep.hasSubstep(element) && !myListPopup.isShowSubmenuOnHover && myStep.isFinal(element)
  }
}