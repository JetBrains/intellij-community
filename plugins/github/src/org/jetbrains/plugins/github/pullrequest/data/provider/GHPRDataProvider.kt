// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

interface GHPRDataProvider {
  val id: GHPRIdentifier
  val detailsData: GHPRDetailsDataProvider
  val changesData: GHPRChangesDataProvider
  val commentsData: GHPRCommentsDataProvider
  val reviewData: GHPRReviewDataProvider
  val aiReviewData: GHPRAIReviewDataProvider
  val viewedStateData: GHPRViewedStateDataProvider
  val timelineLoader: GHListLoader<GHPRTimelineItem>?

  fun acquireTimelineLoader(disposable: Disposable): GHListLoader<GHPRTimelineItem>
}