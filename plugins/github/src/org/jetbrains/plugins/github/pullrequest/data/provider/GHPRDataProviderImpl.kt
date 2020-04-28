// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.openapi.Disposable
import org.jetbrains.plugins.github.pullrequest.data.GHPRTimelineLoaderHolder

internal class GHPRDataProviderImpl(override val detailsData: GHPRDetailsDataProvider,
                                    override val stateData: GHPRStateDataProvider,
                                    override val changesData: GHPRChangesDataProvider,
                                    override val commentsData: GHPRCommentsDataProvider,
                                    override val reviewData: GHPRReviewDataProvider,
                                    private val timelineLoaderHolderDelegate: GHPRTimelineLoaderHolder)
  : GHPRDataProvider {

  override val timelineLoader get() = timelineLoaderHolderDelegate.timelineLoader

  override fun acquireTimelineLoader(disposable: Disposable) =
    timelineLoaderHolderDelegate.acquireTimelineLoader(disposable)

  override fun dispose() {
  }
}