// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.dvcs.repo.Repository
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.repo.GitRepository

private val LOG: Logger = logger<GitRewordAction>()

internal class GitRewordAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.getString("rebase.log.action.operation.reword.name")
  )

  override fun actionPerformedAfterChecks(commitEditingData: SingleCommitEditingData) {
    val details = getOrLoadDetails(commitEditingData.project, commitEditingData.logData, commitEditingData.selectedCommit)

    RewordNewCommitMessageActionDialog(commitEditingData, details).show()
  }

  private fun getOrLoadDetails(project: Project, data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata {
    return commit as? VcsCommitMetadata
           ?: getCommitDataFromCache(data, commit)
           ?: loadCommitData(project, data, commit)
           ?: throw ProcessCanceledException()
  }

  override fun getFailureTitle(): String = GitBundle.getString("rebase.log.reword.action.failure.title")

  private fun getCommitDataFromCache(data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata? {
    val commitIndex = data.getCommitIndex(commit.id, commit.root)
    val commitData = data.commitDetailsGetter.getCommitDataIfAvailable(commitIndex)
    if (commitData != null) return commitData

    val message = data.index.dataGetter?.getFullMessage(commitIndex)
    if (message != null) return VcsCommitMetadataImpl(commit.id, commit.parents, commit.commitTime, commit.root, commit.subject,
                                                      commit.author, message, commit.committer, commit.authorTime)
    return null
  }

  private fun loadCommitData(project: Project, data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata? {
    var commitData: VcsCommitMetadata? = null
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        try {
          commitData = VcsLogUtil.getDetails(data, commit.root, commit.id)
        }
        catch (e: VcsException) {
          val error = GitBundle.message("rebase.log.reword.action.loading.commit.message.failed.message", commit.id.asString())
          LOG.warn(error, e)
          val notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification(
            "",
            error,
            NotificationType.ERROR,
            null
          )
          VcsNotifier.getInstance(project).notify(notification)
        }
      }, GitBundle.getString("rebase.log.reword.action.progress.indicator.loading.commit.message.title"), true, project)
    return commitData
  }

  private fun rewordInBackground(project: Project, commit: VcsCommitMetadata, repository: GitRepository, newMessage: String) {
    object : Task.Backgroundable(project, GitBundle.getString("rebase.log.reword.action.progress.indicator.title")) {
      override fun run(indicator: ProgressIndicator) {
        GitRewordOperation(repository, commit, newMessage).execute()
      }
    }.queue()
  }

  override fun getProhibitedStateMessage(commitEditingData: SingleCommitEditingData, operation: String): String? {
    if (commitEditingData.repository.state == Repository.State.REBASING && commitEditingData.isHeadCommit) {
      return null
    }
    return super.getProhibitedStateMessage(commitEditingData, operation)
  }

  private inner class RewordNewCommitMessageActionDialog(
    commitEditingData: SingleCommitEditingData,
    private val commit: VcsCommitMetadata
  ) : GitNewCommitMessageActionDialog<SingleCommitEditingData>(
    commitEditingData,
    commit.fullMessage,
    GitBundle.message(
      "rebase.log.reword.dialog.description.label",
      commit.id.toShortString(),
      getShortPresentation(commit.author)
    )
  ) {
    init {
      title = GitBundle.getString("rebase.log.reword.dialog.title")
    }

    override fun startOperation(commitEditingData: SingleCommitEditingData, newMessage: String) {
      rewordInBackground(commitEditingData.project, commit, commitEditingData.repository, newMessage)
    }
  }
}