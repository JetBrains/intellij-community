// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState

data class GHPullRequestPendingReview(val id: String, val state: GHPullRequestReviewState, val commentsCount: Int)