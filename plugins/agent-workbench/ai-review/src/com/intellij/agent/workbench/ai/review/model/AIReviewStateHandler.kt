// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review.model

import org.jetbrains.annotations.ApiStatus

/**
 * Interface for handling state changes in AI Review.
 * This interface abstracts from view model (UI) functionality.
 */
@ApiStatus.Internal
interface AIReviewStateHandler {

  fun getCurrentRequest(): AIReviewRequest

  fun setErrorState(e: Throwable?, partialReview: AIReviewResult?)

  fun setPartialReviewState(partialReview: AIReviewResult)

  fun setFullReviewState(fullReview: AIReviewResult)

  fun setCancelledState(partialReview: AIReviewResult?)

  fun setFiltersAppliedState(filterName: String, state: Boolean) {}
}
