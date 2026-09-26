// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.GitDisposable
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle
import git4idea.merge.GitMerger
import git4idea.repo.GitRepository
import icons.DvcsImplIcons
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.swing.Icon

internal class GitMergeCommitAction : GitOperationActionBase(Repository.State.MERGING) {

  override val operationName: String
    get() = GitBundle.message("action.Git.Merge.Commit.text")

  override fun getMainToolbarIcon(): Icon = DvcsImplIcons.ResolveContinue

  private var job: Job? = null

  override fun performInBackground(repository: GitRepository) {
    if (job?.isActive == true) return
    val project = repository.project
    job = GitDisposable.getInstance(project).coroutineScope.launch {
      withBackgroundProgress(project, GitBundle.message("action.Git.Merge.Commit.progress.title")) {
        repository.executeMergeCommit()
      }
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val project = e.project ?: return

    if (e.presentation.isEnabledAndVisible) {
      e.presentation.isEnabledAndVisible = !GitResolveConflictsAction.isEnabled(project)
    }
  }
}

private suspend fun GitRepository.executeMergeCommit() = coroutineToIndicator {
  try {
    GitMerger(project).mergeCommit(root)
  }
  catch (e: VcsException) {
    VcsNotifier.getInstance(project)
      .notifyError(GitNotificationIdsHolder.MERGE_COMMIT_ERROR,
                   GitBundle.message("action.Git.Merge.Commit.error.title"),
                   e.localizedMessage)
  }
  update()
}
