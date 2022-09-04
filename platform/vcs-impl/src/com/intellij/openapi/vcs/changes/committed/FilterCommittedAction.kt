// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.changes.committed.ClearCommittedAction.Companion.getSelectedChangesViewContent
import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings

private fun <S : ChangeBrowserSettings> RepositoryLocationCommittedChangesPanel<S>.setCommittedChangesFilter() {
  val dialog = CommittedChangesFilterDialog(project, provider.createFilterUI(true), settings)
  if (!dialog.showAndGet()) return

  @Suppress("UNCHECKED_CAST")
  settings = dialog.settings as S
  refreshChanges()
}

class FilterCommittedAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getSelectedChangesViewContent<RepositoryLocationCommittedChangesPanel<*>>() != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) =
    e.getSelectedChangesViewContent<RepositoryLocationCommittedChangesPanel<*>>()!!.setCommittedChangesFilter()
}