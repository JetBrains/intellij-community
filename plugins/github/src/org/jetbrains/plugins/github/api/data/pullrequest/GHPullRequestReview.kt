// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

import com.intellij.collaboration.api.dto.GraphQLFragment
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHNode
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import java.util.*

@GraphQLFragment("/graphql/fragment/pullRequestReview.graphql")
data class GHPullRequestReview(override val id: String,
                               val url: String,
                               val author: GHActor?,
                               val body: @NlsSafe String,
                               val state: GHPullRequestReviewState,
                               val createdAt: Date,
                               val viewerCanUpdate: Boolean)
  : GHNode(id), GHPRTimelineItem