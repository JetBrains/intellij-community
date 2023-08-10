// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import java.awt.event.InputEvent
import javax.swing.JComponent

abstract class ExpandableComboAction : AnAction(), CustomComponentAction {
  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ToolbarComboWidget() {
      override fun doExpand(e: InputEvent?) {
        createPopup(e)?.showUnderneathOf(this)
      }

      override fun createPopup(e: InputEvent?): JBPopup? {
        val dataContext = DataManager.getInstance().getDataContext(this)
        val anActionEvent = AnActionEvent.createFromInputEvent(e, place, presentation, dataContext)
        val popup = createPopup(anActionEvent) ?: return null
        popup.addListener(object : JBPopupListener {
          override fun beforeShown(event: LightweightWindowEvent) {
            isPopupShowing = true
          }

          override fun onClosed(event: LightweightWindowEvent) {
            isPopupShowing = false
          }
        })
        return popup
      }
    }
  }

  protected abstract fun createPopup(event: AnActionEvent): JBPopup?

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { createPopup(e)?.showCenteredInCurrentWindow(it) }
  }
}
