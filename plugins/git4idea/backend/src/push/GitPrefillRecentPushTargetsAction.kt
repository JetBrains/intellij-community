// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.config.GitVcsSettings
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager

/**
 * An internal action for the manual testing of the push target history.
 * For each repository of the open project, it fills the history of the current branch
 * with the existing remote branches of that repository.
 */
internal class GitPrefillRecentPushTargetsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = project != null && GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val settings = GitVcsSettings.getInstance(project)
    var storedCount = 0
    for (repository in GitRepositoryManager.getInstance(project).repositories) {
      val sourceBranch = repository.currentBranch?.name ?: continue
      // Add the branches in the reversed name order. The history is most-recent-first,
      // so the stored entries end up in the direct name order.
      val remoteBranches = repository.branches.remoteBranches.sortedByDescending { it.nameForRemoteOperations }
      for (remoteBranch in remoteBranches) {
        settings.addRecentPushTarget(repository.root.path, sourceBranch, remoteBranch.remote.name, remoteBranch.nameForRemoteOperations)
      }
      storedCount += settings.getRecentPushTargets(repository.root.path, sourceBranch).size
    }
    VcsNotifier.getInstance(project).notifyInfo(
      null,
      GitBundle.message("push.recent.targets.prefill.notification.title"),
      GitBundle.message("push.recent.targets.prefill.notification.message", storedCount))
  }
}
