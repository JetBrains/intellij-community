// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose

import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction

/** Internal action to toggle Compose verbose stack traces. */
internal class ComposeVerboseStackTraceAction : DumbAwareToggleAction() {
  init {
    Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.None)
  }

  private var isEnabled: Boolean = false

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = isEnabled

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    isEnabled = state
    Composer.setDiagnosticStackTraceMode(if (isEnabled) ComposeStackTraceMode.SourceInformation else ComposeStackTraceMode.None)
  }
}
