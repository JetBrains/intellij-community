// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.VcsLogData
import git4idea.GitNotificationIdsHolder.Companion.PUSH_NOT_SUPPORTED
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.push.GitPushSource
import git4idea.rebase.log.GitCommitEditingActionBase.Companion.findContainingBranches
import git4idea.repo.GitRepository


class GitPushUpToCommitAction : GitLogSingleCommitAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val log = e.getRequiredData(VcsLogDataKeys.VCS_LOG)
    val logData = e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData
    val commit = ContainerUtil.getFirstItem(log.selectedCommits)!!
    val repository: GitRepository = getRepositoryForRoot(project, commit.root)!!


    //get the current branch or the first local branch which has the selected commit
    val branches = findContainingBranches(logData, repository.root, commit.hash)
    val branch = if (GitUtil.HEAD in branches) repository.currentBranch!!
    else branches.firstNotNullOfOrNull { repository.branches.findLocalBranch(it) }

    if (branch != null) {
      VcsPushDialog(repository.project, listOf(repository), listOf(repository), repository,
        GitPushSource.create(branch, commit.hash.asString())).show()
    }
    else {
      VcsNotifier.getInstance(project).notifyError(PUSH_NOT_SUPPORTED, GitBundle.message("push.upto.here.not.supported.notification.title"),
        GitBundle.message("push.upto.here.not.supported.notification.message"))
    }
  }

  override fun actionPerformed(repository: GitRepository, commit: Hash) {
  }

  override fun isEnabled(repository: GitRepository, commit: Hash): Boolean {
    return repository.isOnBranch
  }
}