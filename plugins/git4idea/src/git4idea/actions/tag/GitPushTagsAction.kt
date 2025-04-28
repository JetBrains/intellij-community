// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.tag

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.getPushSupport
import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitDisposable
import git4idea.GitTag
import git4idea.GitVcs.getInstance
import git4idea.actions.branch.GitBranchActionsDataKeys
import git4idea.i18n.GitBundle
import git4idea.push.*
import git4idea.push.GitPushSource.createTag
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting

internal class GitPushTagAction : GitSingleTagAction(GitBundle.messagePointer("action.Git.Push.Tag.text")) {
  override fun updateIfEnabledAndVisible(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitTag) {
    val remote = e.getData(GitBranchActionsDataKeys.REMOTE)
    val selectedRepo = e.getData(GitBranchActionsDataKeys.SELECTED_REPOSITORY)
    if (remote == null || selectedRepo == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    e.presentation.text = GitBundle.message(
      "action.Git.Push.Tag.text.to",
      remote.name,
      if (repositories.size > 1) 1 else 0,
      DvcsUtil.getShortRepositoryName(selectedRepo)
    )
  }

  override fun actionPerformed(e: AnActionEvent, project: Project, repositories: List<GitRepository>, reference: GitTag) {
    val remote = e.getData(GitBranchActionsDataKeys.REMOTE) ?: return
    val repository = e.getData(GitBranchActionsDataKeys.SELECTED_REPOSITORY) ?: return

    GitDisposable.getInstance(project).childScope("Git push tags").launch {
      withBackgroundProgress(project, DvcsBundle.message("push.process.pushing"), cancellable = true) {
        val pushSupport = getPushSupport(getInstance(project)) as GitPushSupport
        val pushSpec = preparePushSpec(reference, remote)
        pushSupport.pusher.push(mapOf(repository to pushSpec), null, false)
      }
    }
  }

  internal companion object {
    const val ACTION_ID = "Git.Tag.Push"

    @VisibleForTesting
    internal fun preparePushSpec(reference: GitTag, remote: GitRemote): PushSpec<GitPushSource, GitPushTarget> {
      val pushSource = createTag(reference)
      val pushTarget = GitPushTarget(GitSpecialRefRemoteBranch(reference.fullName, remote), true, true, GitPushTargetType.PUSH_SPEC)
      return PushSpec<GitPushSource, GitPushTarget>(pushSource, pushTarget)
    }
  }
}
