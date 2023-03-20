// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.JBPopup
import java.awt.event.InputEvent
import javax.swing.JComponent

abstract class ExpandableComboAction : AnAction(), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    object : ToolbarComboWidget() {
      override fun doExpand(e: InputEvent?) {
        val dataContext = DataManager.getInstance().getDataContext(this)
        val anActionEvent = AnActionEvent.createFromInputEvent(e, place, presentation, dataContext)
        createPopup(anActionEvent)?.showUnderneathOf(this)
      }
    }

  protected abstract fun createPopup(event: AnActionEvent): JBPopup?

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { createPopup(e)?.showCenteredInCurrentWindow(it) }
  }
}
