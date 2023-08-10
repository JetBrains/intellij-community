// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.committed.ClearCommittedAction.Companion.getSelectedChangesViewContent

class RefreshCommittedAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val panel = e.getSelectedChangesViewContent<CommittedChangesPanel>()
    val isLoading = panel is RepositoryLocationCommittedChangesPanel<*> && panel.isLoading

    e.presentation.isEnabled = panel != null && !isLoading
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val panel = e.getSelectedChangesViewContent<CommittedChangesPanel>()!!

    if (panel is RepositoryLocationCommittedChangesPanel<*>)
      panel.refreshChanges()
    else
      RefreshIncomingChangesAction.doRefresh(panel.project)
  }
}