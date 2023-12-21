// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogDataKeys
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundle
import git4idea.push.GitPushSource
import git4idea.rebase.log.GitCommitEditingActionBase
import git4idea.rebase.log.GitCommitEditingActionBase.Companion.checkHeadLinearHistory
import git4idea.rebase.log.GitCommitEditingActionBase.Companion.findContainingBranches
import git4idea.repo.GitRepository


class GitPushUpToCommitAction : GitLogSingleCommitAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val selection = e.getRequiredData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
    val logData = e.getRequiredData(VcsLogDataKeys.VCS_LOG_DATA_PROVIDER) as VcsLogData
    val commit = selection.commits.first()
    val repository: GitRepository = getRepositoryForRoot(project, commit.root)!!


    //get the current branch or the first local branch which has the selected commit
    val currentBranch = repository.currentBranch
    val branches = findContainingBranches(logData, repository.root, commit.hash)
    val branch = if (GitUtil.HEAD in branches && currentBranch != null) currentBranch
    else branches.firstNotNullOfOrNull { repository.branches.findLocalBranch(it) }

    val referenceToPush: GitPushSource
    if (Registry.`is`("git.push.upto.commit.with.head.reference") && branch != null && branch == currentBranch) {
      // for the current branch, we can use HEAD relative reference a.e HEAD^1
      // that allows to re-push properly if an update is needed
      val description = checkHeadLinearHistory(GitCommitEditingActionBase.MultipleCommitEditingData(repository, selection, logData),
                                               GitBundle.message("push.up.to.commit.allowed.progress.title"))
      if (description != null) {
        Messages.showErrorDialog(project, description, GitBundle.message("push.upto.here.failed.dialog.title"))
        return
      }
      val sourceReference = getSourceReference(repository, commit.hash)
      referenceToPush = GitPushSource.createRef(branch, sourceReference ?: commit.hash.asString())
    }
    else if (branch != null) {
      referenceToPush = GitPushSource.createRef(branch, commit.hash.asString())
    }
    else {
      referenceToPush = GitPushSource.createDetached(commit.hash.asString())
    }

    VcsPushDialog(repository.project, listOf(repository), listOf(repository), repository, referenceToPush).show()
  }

  override fun actionPerformed(repository: GitRepository, commit: Hash) {
  }

  private fun getSourceReference(repository: GitRepository, hash: Hash): String? {
    var reference: String? = null
    ProgressManager.getInstance().runProcessWithProgressSynchronously({
      val count = GitHistoryUtils.getNumberOfCommitsBetween(repository, hash.asString(), GitUtil.HEAD)?.toInt()
      count?.let {
        reference = if (count > 0) VcsLogUtil.HEAD + "^" + count else VcsLogUtil.HEAD
      }
    }, GitBundle.message("push.up.to.commit.getting.reference.progress.title"), true, repository.project)
    return reference
  }
}