// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import javax.swing.JComponent

internal class ClearCommittedAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getSelectedChangesViewContent<ProjectCommittedChangesPanel>() != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) =
    e.getSelectedChangesViewContent<ProjectCommittedChangesPanel>()!!.clearCaches()

  companion object {
    internal inline fun <reified T : JComponent> AnActionEvent.getSelectedChangesViewContent(): T? =
      project?.let { ChangesViewContentManager.getInstance(it) }?.getActiveComponent(T::class.java)
  }
}
