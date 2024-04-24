// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.util.CollectionDelta
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHRefUpdateRule
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeabilityData
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import java.util.concurrent.CompletableFuture

interface GHPRDetailsService {

  suspend fun loadDetails(pullRequestId: GHPRIdentifier): GHPullRequest

  suspend fun updateDetails(pullRequestId: GHPRIdentifier, title: String?, description: String?): GHPullRequest

  suspend fun adjustReviewers(pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHPullRequestRequestedReviewer>)

  suspend fun loadMergeabilityState(pullRequestId: GHPRIdentifier): GHPRMergeabilityState

  suspend fun close(pullRequestId: GHPRIdentifier)

  suspend fun reopen(pullRequestId: GHPRIdentifier)

  suspend fun markReadyForReview(pullRequestId: GHPRIdentifier)

  suspend fun merge(pullRequestId: GHPRIdentifier, commitMessage: Pair<String, String>, currentHeadRef: String)

  suspend fun rebaseMerge(pullRequestId: GHPRIdentifier, currentHeadRef: String)

  suspend fun squashMerge(pullRequestId: GHPRIdentifier, commitMessage: Pair<String, String>, currentHeadRef: String)

  //used in "create pr" - convert to coroutines
  @CalledInAny
  fun adjustReviewers(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHPullRequestRequestedReviewer>)
    : CompletableFuture<Unit>

  @CalledInAny
  fun adjustAssignees(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHUser>)
    : CompletableFuture<Unit>

  @CalledInAny
  fun adjustLabels(indicator: ProgressIndicator, pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHLabel>)
    : CompletableFuture<Unit>
}