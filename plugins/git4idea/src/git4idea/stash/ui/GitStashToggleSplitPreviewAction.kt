// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.shouldHaveSplitterDiffPreview
import git4idea.config.GitVcsApplicationSettings

internal class GitStashToggleSplitPreviewAction : DumbAwareToggleAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    if (project == null || !isStashTabVisible(project)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = shouldHaveSplitterDiffPreview(project, isStashTabVertical(project))
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    return GitVcsApplicationSettings.getInstance().isSplitDiffPreviewInStashesEnabled
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    GitVcsApplicationSettings.getInstance().isSplitDiffPreviewInStashesEnabled = state
    ApplicationManager.getApplication().messageBus.syncPublisher(GitStashSettingsListener.TOPIC).onSplitDiffPreviewSettingChanged()
  }
}