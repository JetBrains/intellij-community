// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.popup.ActionPopupStep
import java.awt.Point
import java.awt.event.InputEvent
import javax.swing.JComponent
import javax.swing.JList

private val Empty = object : PopupInlineActionsSupport {
  override fun hasExtraButtons(element: Any): Boolean = false
  override fun calcExtraButtonsCount(element: Any): Int = 0
  override fun calcButtonIndex(element: Any?, point: Point): Int? = null
  override fun runInlineAction(element: Any, index: Int, event: InputEvent?) = false
  override fun getExtraButtons(list: JList<*>, value: Any, isSelected: Boolean): List<JComponent> = emptyList()
  override fun getActiveButtonIndex(list: JList<*>): Int? = null
  override fun getActiveExtraButtonToolTipText(list: JList<*>, value: Any): String? = null
}

internal interface PopupInlineActionsSupport {

  fun hasExtraButtons(element: Any): Boolean

  fun calcExtraButtonsCount(element: Any): Int

  fun calcButtonIndex(element: Any?, point: Point): Int?

  fun runInlineAction(element: Any, index: Int, event: InputEvent? = null) : Boolean

  fun getExtraButtons(list: JList<*>, value: Any, isSelected: Boolean): List<JComponent>

  @ActionText
  fun getActiveExtraButtonToolTipText(list: JList<*>, value: Any): String?

  fun getActiveButtonIndex(list: JList<*>): Int?

  companion object {
    fun create(popup: ListPopupImpl): PopupInlineActionsSupport =
      if (ExperimentalUI.isNewUI() && popup.listStep is ActionPopupStep) PopupInlineActionsSupportImpl(popup)
      else Empty
  }
}