// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState

internal interface GHPRDetailsViewModel : CodeReviewDetailsViewModel {
  val description: Flow<String>
  val viewerDidAuthor: Boolean
  val isDraft: Flow<Boolean>

  val mergeabilityState: Flow<GHPRMergeabilityState?>
  val checksState: Flow<GHPRMergeabilityState.ChecksState>
  val hasConflicts: Flow<Boolean?>
  val isRestricted: Flow<Boolean>
  val requiredApprovingReviewsCount: Flow<Int>
}