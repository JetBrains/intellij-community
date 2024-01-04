// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch

import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.popup.PopupDispatcher
import com.intellij.ui.popup.WizardPopup
import git4idea.actions.GitFetch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.fetch.GitFetchResult
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import kotlin.streams.asSequence

class GitBranchPopupFetchAction<P : WizardPopup>(private val popupClass: Class<P>) : GitFetch() {

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project
    val presentation = e.presentation

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

  override fun onFetchFinished(project: Project, result: GitFetchResult) {
    GitBranchIncomingOutgoingManager.getInstance(project)
      .forceUpdateBranches { ActivityTracker.getInstance().inc() }
    showNotificationIfNeeded(result)
  }

  private fun showNotificationIfNeeded(result: GitFetchResult) {
    val popup = PopupDispatcher.getInstance().popupStream.asSequence().find(popupClass.javaClass::isInstance)

    if (popup != null) {
      result.showNotificationIfFailed()
    }
    else {
      result.showNotification()
    }
  }

  private fun isBusy(project: Project): Boolean {
    return GitFetchSupport.fetchSupport(project).isFetchRunning
           || GitBranchIncomingOutgoingManager.getInstance(project).isUpdating
  }
}
