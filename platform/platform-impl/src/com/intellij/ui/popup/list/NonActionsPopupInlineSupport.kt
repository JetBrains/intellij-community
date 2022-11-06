// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import java.awt.Point
import java.awt.event.InputEvent
import javax.swing.JComponent
import javax.swing.JList

class NonActionsPopupInlineSupport(private val myListPopup: ListPopupImpl) : PopupInlineActionsSupport {
  override fun calcExtraButtonsCount(element: Any?): Int = if (hasMoreButton(element)) 1 else 0

  override fun calcButtonIndex(element: Any?, point: Point): Int? {
    if (element == null || !hasMoreButton(element)) return null
    return calcButtonIndex(myListPopup.list, 1, point)
  }

  override fun runInlineAction(element: Any?, index: Int, event: InputEvent?): Boolean {
    if (index == 0 && hasMoreButton(element)) myListPopup.showNextStepPopup(myListPopup.listStep.onChosen(element, false), element)
    return false
  }

  override fun getExtraButtons(list: JList<*>, value: Any?, isSelected: Boolean): List<JComponent> =
    if (hasMoreButton(value) && isSelected) listOf(createExtraButton (AllIcons.Actions.More, getActiveButtonIndex(list) == 0))
    else emptyList()

  override fun getActiveExtraButtonToolTipText(list: JList<*>, value: Any?): String? =
    if (hasMoreButton(value) && getActiveButtonIndex(list) == 0) IdeBundle.message("inline.actions.more.actions.text")
    else null

  override fun getActiveButtonIndex(list: JList<*>): Int? = (list as? ListPopupImpl.ListWithInlineButtons)?.selectedButtonIndex

  private fun hasMoreButton(element: Any?) = myListPopup.listStep.hasSubstep(element)
                                            && !myListPopup.isShowSubmenuOnHover
                                            && myListPopup.listStep.isFinal(element)
}