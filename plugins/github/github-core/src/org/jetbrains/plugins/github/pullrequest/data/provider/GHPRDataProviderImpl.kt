// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier

internal class GHPRDataProviderImpl(
  override val id: GHPRIdentifier,
  override val detailsData: GHPRDetailsDataProvider,
  override val timelineData: GHPRTimelineDataProvider,
  override val changesData: GHPRChangesDataProvider,
  override val commentsData: GHPRCommentsDataProvider,
  override val reviewData: GHPRReviewDataProvider,
  override val viewedStateData: GHPRViewedStateDataProvider,
) : GHPRDataProvider