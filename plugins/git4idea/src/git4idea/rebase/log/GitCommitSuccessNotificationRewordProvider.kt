// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.log

import com.intellij.notification.NotificationAction
import com.intellij.openapi.components.service
import com.intellij.vcs.commit.CommitNotification
import com.intellij.vcs.commit.CommitSuccessNotificationActionProvider
import com.intellij.vcs.commit.VcsCommitter
import git4idea.checkin.GitPostCommitChangeConverter
import git4idea.i18n.GitBundle
import git4idea.rebase.GitRewordService
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

internal class GitCommitSuccessNotificationRewordProvider : CommitSuccessNotificationActionProvider {
  override fun getActions(committer: VcsCommitter, notification: CommitNotification): List<NotificationAction> {
    val repoWithCommitHash = GitPostCommitChangeConverter.getRecordedPostCommitHashes(committer.commitContext) ?: return emptyList()
    if (repoWithCommitHash.isEmpty()) return emptyList()
    val project = repoWithCommitHash.keys.first().project
    val connection = project.messageBus.connect()
    notification.whenExpired { connection.disconnect() }
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repo ->
      if (repo.currentRevision != repoWithCommitHash[repo]?.asString()) {
        notification.expire()
      }
    })
    val rewordAction = NotificationAction.createSimpleExpiring(GitBundle.message("action.Git.Reword.Commit.text")) {
      project.service<GitRewordService>().launchReword(repoWithCommitHash)
    }
    return listOf(rewordAction)
  }
}