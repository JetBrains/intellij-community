// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.UIUtil

class GotoNextFloatingToolbarMenu: AnAction() {

  companion object {
    fun showNextMenu(event: AnActionEvent, isForwardDirection: Boolean) {
      val project = event.project ?: return
      val floatingToolbar = findFloatingToolbar(event) ?: return
      val hintComponent = floatingToolbar.hintComponent ?: return
      val allButtons = UIUtil.findComponentsOfType(hintComponent, ActionButton::class.java)
      val navigatableButtons = allButtons.filter { it.presentation.isEnabledAndVisible }
      val selectedIndex = navigatableButtons.indexOfFirst { button -> Toggleable.isSelected(button.presentation) }
      if (selectedIndex < 0) return
      val nextIndex = if (isForwardDirection) selectedIndex + 1 else selectedIndex - 1
      val button = navigatableButtons[nextIndex.mod(navigatableButtons.size)]
      if (button.presentation.isPopupGroup) {
        button.click()
      }
      else {
        showActionInPopup(project, floatingToolbar, button)
      }
    }

    fun findFloatingToolbar(e: AnActionEvent): CodeFloatingToolbar? {
      val project = e.project ?: return null
      val selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor()
      val toolbar = CodeFloatingToolbar.getToolbar(selectedEditor) ?: return null
      if (!toolbar.isShown()) return null
      return toolbar
    }

    private fun showActionInPopup(project: Project, floatingToolbar: CodeFloatingToolbar, button: ActionButton){
      val editor = FileEditorManager.getInstance(project).getSelectedTextEditor() ?: return
      val popup = JBPopupFactory.getInstance().createActionGroupPopup(
        null,
        DefaultActionGroup(button.action),
        DataManager.getInstance().getDataContext(editor.component),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true
      )
      floatingToolbar.attachPopupToButton(button, popup)
      popup.showUnderneathOf(button)
    }
  }

  init {
    isEnabledInModalContext = true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = (findFloatingToolbar(e) != null)
  }

  override fun actionPerformed(e: AnActionEvent) {
    showNextMenu(e, isForwardDirection = true)
  }
}