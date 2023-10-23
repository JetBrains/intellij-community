// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.util.ui.UIUtil

class GotoNextFloatingToolbarMenu: AnAction() {

  companion object {
    fun showNextMenu(floatingToolbar: CodeFloatingToolbar?, isForwardDirection: Boolean){
      val hintComponent = floatingToolbar?.hintComponent ?: return
      val components = UIUtil.findComponentsOfType(hintComponent, ActionButton::class.java).filter { it.presentation.isPopupGroup }
      val selectedIndex = components.indexOfFirst { component -> Toggleable.isSelected(component.presentation) }
      if (selectedIndex < 0) return
      val nextIndex = if (isForwardDirection) selectedIndex + 1 else selectedIndex - 1
      val button = components[nextIndex.mod(components.size)]
      button.click()
    }

    fun findFloatingToolbar(e: AnActionEvent): CodeFloatingToolbar? {
      val project = e.project ?: return null
      val selectedEditor = FileEditorManager.getInstance(project).getSelectedTextEditor()
      val toolbar = CodeFloatingToolbar.getToolbar(selectedEditor) ?: return null
      if (!toolbar.isShown()) return null
      return toolbar
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
    showNextMenu(findFloatingToolbar(e), isForwardDirection = true)
  }
}