// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.codeFloatingToolbar.GotoNextFloatingToolbarMenu.Companion.findFloatingToolbar
import com.intellij.ui.codeFloatingToolbar.GotoNextFloatingToolbarMenu.Companion.showNextMenu

class GotoPreviousFloatingToolbarMenu: AnAction() {

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
    showNextMenu(e, isForwardDirection = false)
  }
}