// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.service

import com.intellij.collaboration.util.CollectionDelta
import org.jetbrains.plugins.github.api.data.GHLabel
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState

internal interface GHPRDetailsService {
  suspend fun findPRId(number: Long): GHPRIdentifier?

  suspend fun loadDetails(pullRequestId: GHPRIdentifier): GHPullRequest

  suspend fun updateDetails(pullRequestId: GHPRIdentifier, title: String?, description: String?): GHPullRequest

  suspend fun adjustReviewers(pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHPullRequestRequestedReviewer>)

  suspend fun adjustAssignees(pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHUser>)

  suspend fun adjustLabels(pullRequestId: GHPRIdentifier, delta: CollectionDelta<GHLabel>)

  suspend fun loadMergeabilityState(pullRequestId: GHPRIdentifier): GHPRMergeabilityState

  suspend fun close(pullRequestId: GHPRIdentifier)

  suspend fun reopen(pullRequestId: GHPRIdentifier)

  suspend fun markReadyForReview(pullRequestId: GHPRIdentifier)

  suspend fun merge(pullRequestId: GHPRIdentifier, commitMessage: Pair<String, String>, currentHeadRef: String)

  suspend fun rebaseMerge(pullRequestId: GHPRIdentifier, currentHeadRef: String)

  suspend fun squashMerge(pullRequestId: GHPRIdentifier, commitMessage: Pair<String, String>, currentHeadRef: String)
}