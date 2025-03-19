// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import java.awt.Point
import java.awt.event.InputEvent
import javax.swing.JComponent

internal class NonActionsPopupInlineSupport(private val myListPopup: ListPopupImpl) : PopupInlineActionsSupport {
  override fun calcExtraButtonsCount(element: Any?): Int = if (hasMoreButton(element)) 1 else 0

  override fun calcButtonIndex(element: Any?, point: Point): Int? {
    if (element == null || !hasMoreButton(element)) return null
    return calcButtonIndex(myListPopup.list, 1, point)
  }

  override fun getToolTipText(element: Any?, index: Int): String? = when {
    isMoreButton(element, index) -> IdeBundle.message("inline.actions.more.actions.text")
    else -> null
  }

  override fun getKeepPopupOnPerform(element: Any?, index: Int): KeepPopupOnPerform {
    return KeepPopupOnPerform.Always
  }

  override fun performAction(element: Any?, index: Int, event: InputEvent?) {
    if (isMoreButton(element, index)) {
      myListPopup.showNextStepPopup(myListPopup.listStep.onChosen(element, false), element)
    }
  }

  override fun createExtraButtons(value: Any?, isSelected: Boolean, activeButtonIndex: Int): List<JComponent> = when {
    hasMoreButton(value) && isSelected -> listOf(
      createExtraButton(AllIcons.Actions.More, activeButtonIndex == 0))
    else -> emptyList()
  }

  override fun isMoreButton(element: Any?, index: Int): Boolean {
    return hasMoreButton(element) && index == 0
  }

  private fun hasMoreButton(element: Any?): Boolean {
    return myListPopup.listStep.hasSubstep(element) && !myListPopup.isShowSubmenuOnHover && myListPopup.listStep.isFinal(element)
  }
}