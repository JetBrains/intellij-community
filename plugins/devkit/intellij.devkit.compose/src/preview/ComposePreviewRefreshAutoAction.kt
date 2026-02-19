// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.preview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareToggleAction

internal class ComposePreviewRefreshAutoAction : DumbAwareToggleAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    val p = e.project ?: return false

    return p.service<ComposePreviewChangesTracker>().isAutoRefresh()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val p = e.project ?: return

    val tracker = p.service<ComposePreviewChangesTracker>()
    tracker.setAutoRefresh(state)
    if (state) {
      tracker.refresh()
    }
  }
}
