package com.intellij.ui.filterField

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import javax.swing.JComponent

class FilterFieldAction(val supplier: () -> FilterField) : AnAction(), CustomComponentAction {
  override fun update(e: AnActionEvent) {}

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return supplier.invoke()
  }
}