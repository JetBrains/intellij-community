// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.ide.productMode.IdeProductMode

internal class ShowStructuralChangesAction : ToggleAction(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.project != null && !IdeProductMode.isFrontend
  }

  override fun isSelected(e: AnActionEvent): Boolean = e.project?.let(StructuralChangesViewSettings::isEnabled) ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.project?.let { StructuralChangesViewSettings.setEnabled(it, state) }
  }
}
