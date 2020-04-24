// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.GHNotFoundException
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError
import org.jetbrains.plugins.github.util.submitIOTask
import java.util.concurrent.CompletableFuture

class GHPRDetailsServiceImpl(private val progressManager: ProgressManager,
                             private val requestExecutor: GithubApiRequestExecutor,
                             private val repository: GHRepositoryCoordinates) : GHPRDetailsService {

  override fun loadDetails(progressIndicator: ProgressIndicator, pullRequestId: GHPRIdentifier): CompletableFuture<GHPullRequest> =
    progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it, GHGQLRequests.PullRequest.findOne(repository, pullRequestId.number))
      ?: throw GHNotFoundException("Pull request ${pullRequestId.number} does not exist")
    }.logError(LOG, "Error occurred while loading PR details")

  companion object {
    private val LOG = logger<GHPRDetailsService>()
  }
}