// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.computationStateFlow
import com.intellij.collaboration.async.withInitial
import com.intellij.collaboration.util.CollectionDelta
import com.intellij.collaboration.util.ComputedResult
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState

interface GHPRDetailsDataProvider {
  @get:ApiStatus.Internal
  val loadedDetails: GHPullRequest?

  val stateChangeSignal: Flow<Unit>

  val detailsNeedReloadSignal: Flow<Unit>
  val mergeabilityNeedsReloadSignal: Flow<Unit>

  suspend fun loadDetails(): GHPullRequest

  suspend fun loadMergeabilityState(): GHPRMergeabilityState

  suspend fun updateDetails(title: String? = null, description: String? = null): GHPullRequest

  suspend fun adjustReviewers(delta: CollectionDelta<GHPullRequestRequestedReviewer>)

  suspend fun close()

  suspend fun reopen()

  suspend fun markReadyForReview()

  suspend fun merge(commitMessage: Pair<String, String>, currentHeadRef: String)

  suspend fun rebaseMerge(currentHeadRef: String)

  suspend fun squashMerge(commitMessage: Pair<String, String>, currentHeadRef: String)

  suspend fun signalDetailsNeedReload()

  suspend fun signalMergeabilityNeedsReload()
}

val GHPRDetailsDataProvider.detailsComputationFlow: Flow<ComputedResult<GHPullRequest>>
  get() = computationStateFlow(detailsNeedReloadSignal.withInitial(Unit)) { loadDetails() }

val GHPRDetailsDataProvider.mergeabilityStateComputationFlow: Flow<ComputedResult<GHPRMergeabilityState>>
  get() = computationStateFlow(mergeabilityNeedsReloadSignal.withInitial(Unit)) { loadMergeabilityState() }