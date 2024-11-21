// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent

sealed interface GHPRTimelineItem {
  interface Comment : GHPRTimelineItem, GHPRTimelineCommentViewModel
  interface Review : GHPRTimelineItem, GHPRTimelineReviewViewModel
  data class Event(val event: GHPRTimelineEvent) : GHPRTimelineItem
  data class Commits(val commits: List<GHPullRequestCommitShort>) : GHPRTimelineItem
  data class Unknown(val typename: String) : GHPRTimelineItem
}