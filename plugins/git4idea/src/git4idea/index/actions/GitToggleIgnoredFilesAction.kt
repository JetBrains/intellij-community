// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.VcsBundle
import git4idea.index.ui.GitStageDataKeys

class GitToggleIgnoredFilesAction : DumbAwareToggleAction(VcsBundle.messagePointer("changes.action.show.ignored.text"),
                                                          VcsBundle.messagePointer("changes.action.show.ignored.description"),
                                                          AllIcons.Actions.ShowHiddens) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getData(GitStageDataKeys.GIT_STAGE_UI_SETTINGS)?.ignoredFilesShown() ?: return false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(GitStageDataKeys.GIT_STAGE_UI_SETTINGS)?.setIgnoredFilesShown(state)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.getData(GitStageDataKeys.GIT_STAGE_UI_SETTINGS) == null) e.presentation.isEnabledAndVisible = false
  }
}