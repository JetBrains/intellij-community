// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest

enum class GHPullRequestReviewDecision {
    /**
     * The pull request has received an approving review
     */
    APPROVED,

    /**
     * Changes have been requested on the pull request
     */
    CHANGES_REQUESTED,

    /**
     * A review is required before the pull request can be merged
     */
    REVIEW_REQUIRED
  }