// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.CodeReviewDomainEntity
import com.intellij.collaboration.util.ComputableSequence
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

@CodeReviewDomainEntity // pull request
interface GHPRDataProvider {
  val id: GHPRIdentifier
  val detailsData: GHPRDetailsDataProvider
  val timelineData: GHPRTimelineDataProvider
  val changesData: GHPRChangesDataProvider
  val commentsData: GHPRCommentsDataProvider
  val reviewData: GHPRReviewDataProvider
  val viewedStateData: GHPRViewedStateDataProvider
}