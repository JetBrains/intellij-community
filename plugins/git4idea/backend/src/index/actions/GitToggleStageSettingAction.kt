// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.vcs.VcsBundle
import git4idea.index.ui.GitStageDataKeys
import git4idea.index.ui.GitStageUiSettings

abstract class GitToggleStageSettingAction : DumbAwareToggleAction {

  constructor() : super()

  abstract var GitStageUiSettings.setting: Boolean

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    return e.getData(GitStageDataKeys.GIT_STAGE_UI_SETTINGS)?.setting ?: return false
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(GitStageDataKeys.GIT_STAGE_UI_SETTINGS)?.setting = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.getData(GitStageDataKeys.GIT_STAGE_UI_SETTINGS) == null) e.presentation.isEnabledAndVisible = false
  }
}

internal class GitToggleIgnoredFilesAction : GitToggleStageSettingAction() {
  init {
    templatePresentation.setText(VcsBundle.messagePointer("action.ChangesView.ShowIgnored.text"))
    templatePresentation.setDescription(VcsBundle.messagePointer("action.ChangesView.ShowIgnored.description"))
  }

  override var GitStageUiSettings.setting: Boolean
    get() = ignoredFilesShown
    set(value) { ignoredFilesShown = value }
}

internal class GitToggleCommitAllAction : GitToggleStageSettingAction() {
  override var GitStageUiSettings.setting: Boolean
    get() = isCommitAllEnabled
    set(value) { isCommitAllEnabled = value }
}
