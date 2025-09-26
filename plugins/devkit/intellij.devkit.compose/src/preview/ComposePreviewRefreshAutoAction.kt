// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

internal class ComposePreviewRefreshAutoAction : DumbAwareToggleAction() {
  private var refresh: Boolean = true

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    return this.refresh
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    this.refresh = state
  }
}
