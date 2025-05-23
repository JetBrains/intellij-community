// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import git4idea.actions.GitFetch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.ui.branch.popup.GitBranchesTreePopupBase

internal class GitBranchPopupFetchAction : GitFetch() {
  init {
    templatePresentation.setText(GitBundle.messagePointer("action.fetch.text"))
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    if (e.place != GitBranchesTreePopupBase.TOP_LEVEL_ACTION_PLACE) {
      presentation.isEnabledAndVisible = false
      return
    }

    super.update(e)
    val project = e.project

    if (project == null) {
      presentation.isEnabledAndVisible = false
      return
    }
    val busy = isBusy(project)
    if (busy) {
      presentation.isEnabled = true // for loading icon animation
    }
    presentation.icon = if (busy) AnimatedIcon.Default.INSTANCE else AllIcons.Vcs.Fetch
    presentation.text = if (busy) GitBundle.message("fetching") else GitBundle.message("action.fetch.text")
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null || isBusy(project)) return

    super.actionPerformed(e)
  }

  private fun isBusy(project: Project): Boolean {
    return GitFetchSupport.fetchSupport(project).isFetchRunning
           || GitBranchIncomingOutgoingManager.getInstance(project).isUpdating
  }
}
