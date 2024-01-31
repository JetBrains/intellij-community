// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.async.CompletableFutureUtil.submitIOTask
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.io.await
import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHReaction
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.pullrequest.data.service.GHServiceUtil.logError

private val LOG = logger<GHReactionsService>()

interface GHReactionsService {
  suspend fun addReaction(subjectId: String, reaction: GHReactionContent): GHReaction
  suspend fun removeReaction(subjectId: String, reaction: GHReactionContent): GHReaction
}

internal class GHReactionsServiceImpl(
  private val progressManager: ProgressManager,
  private val requestExecutor: GithubApiRequestExecutor,
  private val repository: GHRepositoryCoordinates
) : GHReactionsService {
  override suspend fun addReaction(subjectId: String, reaction: GHReactionContent): GHReaction {
    val progressIndicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    return progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it, GHGQLRequests.Comment.addReaction(repository.serverPath, subjectId, reaction))
    }.logError(LOG, "Error occurred while adding reaction").await()
  }

  override suspend fun removeReaction(subjectId: String, reaction: GHReactionContent): GHReaction {
    val progressIndicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    return progressManager.submitIOTask(progressIndicator) {
      requestExecutor.execute(it, GHGQLRequests.Comment.removeReaction(repository.serverPath, subjectId, reaction))
    }.logError(LOG, "Error occurred while removing reaction").await()
  }
}