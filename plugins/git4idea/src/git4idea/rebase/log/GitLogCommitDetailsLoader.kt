// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.VcsCommitMetadataImpl
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitNotificationIdsHolder
import git4idea.i18n.GitBundle

private val LOG = Logger.getInstance("Git.Rebase.Log.Action.CommitDetailsLoader")

internal fun getOrLoadDetails(project: Project, data: VcsLogData, commitList: List<VcsShortCommitDetails>): List<VcsCommitMetadata> {
  val commitsToLoad = HashSet<VcsShortCommitDetails>(commitList)
  val result = HashMap<VcsShortCommitDetails, VcsCommitMetadata>()
  commitList.forEach { commit ->
    val commitMetadata = (commit as? VcsCommitMetadata) ?: getCommitDataFromCache(data, commit)
    if (commitMetadata != null) {
      result[commit] = commitMetadata
    }
    else {
      commitsToLoad.add(commit)
    }
  }
  val loadedDetails = loadDetails(project, data, commitsToLoad)
  result.putAll(loadedDetails)
  return commitList.map {
    result[it] ?: throw LoadCommitDetailsException()
  }
}

private fun getCommitDataFromCache(data: VcsLogData, commit: VcsShortCommitDetails): VcsCommitMetadata? {
  val commitIndex = data.getCommitIndex(commit.id, commit.root)
  val commitData = data.commitDetailsGetter.getCommitDataIfAvailable(commitIndex)
  if (commitData != null) {
    return commitData
  }

  val message = data.index.dataGetter?.getFullMessage(commitIndex)
  if (message != null) {
    return VcsCommitMetadataImpl(commit.id, commit.parents, commit.commitTime, commit.root, commit.subject,
                                 commit.author, message, commit.committer, commit.authorTime)
  }
  return null
}

private fun loadDetails(
  project: Project,
  data: VcsLogData,
  commits: Collection<VcsShortCommitDetails>
): Map<VcsShortCommitDetails, VcsCommitMetadata> {
  val result = HashMap<VcsShortCommitDetails, VcsCommitMetadata>()
  ProgressManager.getInstance().runProcessWithProgressSynchronously(
    {
      try {
        val commitList = commits.toList()
        if (commitList.isEmpty()) {
          return@runProcessWithProgressSynchronously
        }
        val root = commits.first().root
        val commitListData = VcsLogUtil.getDetails(data.getLogProvider(root), root, commitList.map { it.id.asString() })
        result.putAll(commitList.zip(commitListData))
      }
      catch (e: VcsException) {
        val error = GitBundle.message("rebase.log.action.loading.commit.message.failed.message", commits.size)
        LOG.warn(error, e)
        val notification = VcsNotifier.STANDARD_NOTIFICATION
          .createNotification(error, NotificationType.ERROR)
          .setDisplayId(GitNotificationIdsHolder.COULD_NOT_LOAD_CHANGES_OF_COMMIT_LOG)
        VcsNotifier.getInstance(project).notify(notification)
      }
    },
    GitBundle.message("rebase.log.action.progress.indicator.loading.commit.message.title", commits.size),
    true,
    project
  )
  return result
}

internal class LoadCommitDetailsException : Exception()
