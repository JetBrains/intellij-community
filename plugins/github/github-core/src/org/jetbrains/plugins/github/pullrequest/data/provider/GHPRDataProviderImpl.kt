// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.util.DisposalCountingHolder

internal class GHPRDataProviderImpl(override val id: GHPRIdentifier,
                                    override val detailsData: GHPRDetailsDataProvider,
                                    override val changesData: GHPRChangesDataProvider,
                                    override val commentsData: GHPRCommentsDataProvider,
                                    override val reviewData: GHPRReviewDataProvider,
                                    override val viewedStateData: GHPRViewedStateDataProvider,
                                    private val timelineLoaderHolder: DisposalCountingHolder<GHListLoader<GHPRTimelineItem>>)
  : GHPRDataProvider {

  override val timelineLoader get() = timelineLoaderHolder.value

  override fun acquireTimelineLoader(disposable: Disposable) =
    timelineLoaderHolder.acquireValue(disposable)
}