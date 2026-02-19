// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.AbstractDataGetter.Companion.getCommitDetails
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.table.size
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle

private val LOG = Logger.getInstance("Git.Rebase.Log.Action.CommitDetailsLoader")

internal fun getOrLoadDetails(project: Project, data: VcsLogData, selection: VcsLogCommitSelection): List<VcsCommitMetadata> {
  val cachedCommits = ArrayList(selection.cachedMetadata)
  if (cachedCommits.none { it is LoadingDetails }) return cachedCommits

  return loadDetails(project, data, selection)
}

internal fun getOrLoadSingleCommitDetails(project: Project, data: VcsLogData, selection: VcsLogCommitSelection): VcsCommitMetadata {
  return getOrLoadDetails(project, data, selection).single()
}

private fun loadDetails(project: Project, data: VcsLogData, selection: VcsLogCommitSelection): List<VcsCommitMetadata> {
  try {
    val loadedDetails = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      ThrowableComputable<List<VcsCommitMetadata>, VcsException> {
        return@ThrowableComputable data.miniDetailsGetter.getCommitDetails(selection.ids)
      },
      GitBundle.message("rebase.log.action.progress.indicator.loading.commit.message.title", selection.size),
      true,
      project
    )
    if (loadedDetails.size != selection.size) throw LoadCommitDetailsException()
    return loadedDetails
  }
  catch (e: VcsException) {
    val error = GitBundle.message("rebase.log.action.loading.commit.message.failed.message", selection.size)
    LOG.warn(error, e)
    val notification = VcsNotifier.standardNotification()
      .createNotification(error, NotificationType.ERROR)
      .setDisplayId(GitNotificationIdsHolder.COULD_NOT_LOAD_CHANGES_OF_COMMIT_LOG)
    VcsNotifier.getInstance(project).notify(notification)
    throw LoadCommitDetailsException()
  }
}

internal class LoadCommitDetailsException : Exception()
