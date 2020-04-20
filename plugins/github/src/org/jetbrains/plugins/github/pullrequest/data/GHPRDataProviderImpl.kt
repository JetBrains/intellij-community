// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.openapi.Disposable

internal class GHPRDataProviderImpl(override val detailsData: GHPRDetailsDataProvider,
                                    override val stateData: GHPRStateDataProvider,
                                    override val changesData: GHPRChangesDataProvider,
                                    override val commentsData: GHPRCommentsDataProvider,
                                    override val reviewData: GHPRReviewDataProvider,
                                    timelineLoaderFactory: () -> GHPRTimelineLoader)
  : GHPRDataProvider {

  private val timelineLoaderHolder = GHPRCountingTimelineLoaderHolder(timelineLoaderFactory)

  override val timelineLoader get() = timelineLoaderHolder.timelineLoader

  override fun acquireTimelineLoader(disposable: Disposable) = timelineLoaderHolder.acquireTimelineLoader(disposable)

  override fun dispose() {
  }
}