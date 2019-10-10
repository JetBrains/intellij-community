// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.messages.MessageBus
import org.jetbrains.plugins.github.api.GHRepositoryPath
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.GithubApiRequests
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GithubIssueState
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestsDataContext.Companion.PULL_REQUEST_EDITED_TOPIC
import org.jetbrains.plugins.github.util.submitIOTask

class GithubPullRequestsStateServiceImpl internal constructor(private val progressManager: ProgressManager,
                                                              private val messageBus: MessageBus,
                                                              private val requestExecutor: GithubApiRequestExecutor,
                                                              private val serverPath: GithubServerPath,
                                                              private val repoPath: GHRepositoryPath)
  : GithubPullRequestsStateService {

  override fun close(progressIndicator: ProgressIndicator, pullRequest: Long) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.owner, repoPath.repository, pullRequest,
                                                                          state = GithubIssueState.closed))
      messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
    }

  override fun reopen(progressIndicator: ProgressIndicator, pullRequest: Long) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.update(serverPath, repoPath.owner, repoPath.repository, pullRequest,
                                                                          state = GithubIssueState.open))
      messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
    }

  override fun merge(progressIndicator: ProgressIndicator, pullRequest: Long,
                     commitMessage: Pair<String, String>, currentHeadRef: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it, GithubApiRequests.Repos.PullRequests.merge(serverPath, repoPath, pullRequest,
                                                                             commitMessage.first, commitMessage.second,
                                                                             currentHeadRef))
      messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
    }


  override fun rebaseMerge(progressIndicator: ProgressIndicator, pullRequest: Long,
                           currentHeadRef: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.rebaseMerge(serverPath, repoPath, pullRequest,
                                                                               currentHeadRef))
      messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
    }

  override fun squashMerge(progressIndicator: ProgressIndicator, pullRequest: Long,
                           commitMessage: Pair<String, String>, currentHeadRef: String) =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it,
                              GithubApiRequests.Repos.PullRequests.squashMerge(serverPath, repoPath, pullRequest,
                                                                               commitMessage.first, commitMessage.second,
                                                                               currentHeadRef))
      messageBus.syncPublisher(PULL_REQUEST_EDITED_TOPIC).onPullRequestEdited(pullRequest)
    }
}
