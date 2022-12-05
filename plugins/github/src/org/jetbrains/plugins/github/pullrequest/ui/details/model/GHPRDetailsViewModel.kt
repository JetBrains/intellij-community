// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState

internal interface GHPRDetailsViewModel {
  val titleState: StateFlow<String>
  val numberState: StateFlow<String>
  val urlState: StateFlow<String>
  val descriptionState: StateFlow<String>
  val reviewMergeState: StateFlow<GHPullRequestState>

  val isDraftState: StateFlow<Boolean>
  val viewerDidAuthorState: Boolean

  val mergeabilityState: StateFlow<GHPRMergeabilityState?>
  val hasConflictsState: StateFlow<Boolean?>
  val isRestrictedState: StateFlow<Boolean>
  val checksState: StateFlow<GHPRMergeabilityState.ChecksState>
  val requiredApprovingReviewsCountState: StateFlow<Int>
}