// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.api.page.ApiPageUtil
import com.intellij.collaboration.util.ResultUtil.processErrorAndGet
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.flow.fold
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestChangedFile
import org.jetbrains.plugins.github.api.executeSuspend
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

private val LOG = logger<GHPRFilesServiceImpl>()

class GHPRFilesServiceImpl(
  private val requestExecutor: GithubApiRequestExecutor,
  private val repository: GHRepositoryCoordinates
) : GHPRFilesService {

  override suspend fun loadFiles(pullRequestId: GHPRIdentifier): List<GHPullRequestChangedFile> =
    runCatching {
      ApiPageUtil.createGQLPagesFlow {
        requestExecutor.executeSuspend(GHGQLRequests.PullRequest.files(repository, pullRequestId.number, it))
      }.fold(mutableListOf<GHPullRequestChangedFile>()) { acc, value ->
        acc.addAll(value.nodes)
        acc
      }
    }.processErrorAndGet {
      LOG.info("Error occurred while loading pull request files", it)
    }

  override suspend fun updateViewedState(pullRequestId: GHPRIdentifier, path: String, isViewed: Boolean) =
    runCatching {
      val request = if (isViewed) {
        GHGQLRequests.PullRequest.markFileAsViewed(repository.serverPath, pullRequestId.id, path)
      }
      else {
        GHGQLRequests.PullRequest.unmarkFileAsViewed(repository.serverPath, pullRequestId.id, path)
      }
      requestExecutor.executeSuspend(request)
    }.processErrorAndGet {
      LOG.info("Error occurred while updating file viewed state", it)
    }
}