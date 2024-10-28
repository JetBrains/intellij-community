// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import org.jetbrains.plugins.github.api.GHGQLRequests
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor
import org.jetbrains.plugins.github.api.data.GHReaction
import org.jetbrains.plugins.github.api.data.GHReactionContent
import org.jetbrains.plugins.github.api.executeSuspend

internal interface GHReactionsService {
  suspend fun addReaction(subjectId: String, reaction: GHReactionContent): GHReaction
  suspend fun removeReaction(subjectId: String, reaction: GHReactionContent): GHReaction
}

internal class GHReactionsServiceImpl(
  private val requestExecutor: GithubApiRequestExecutor,
  private val repository: GHRepositoryCoordinates
) : GHReactionsService {
  override suspend fun addReaction(subjectId: String, reaction: GHReactionContent): GHReaction {
    val request = GHGQLRequests.Comment.addReaction(repository.serverPath, subjectId, reaction)
    return requestExecutor.executeSuspend(request)
  }

  override suspend fun removeReaction(subjectId: String, reaction: GHReactionContent): GHReaction {
    val request = GHGQLRequests.Comment.removeReaction(repository.serverPath, subjectId, reaction)
    return requestExecutor.executeSuspend(request)
  }
}