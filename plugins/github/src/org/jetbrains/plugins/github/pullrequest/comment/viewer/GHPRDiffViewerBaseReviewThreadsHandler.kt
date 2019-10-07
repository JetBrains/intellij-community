// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.base.DiffViewerBase
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffRangeMapping
import org.jetbrains.plugins.github.pullrequest.data.model.GHPRDiffReviewThreadMapping
import kotlin.properties.Delegates.observable

abstract class GHPRDiffViewerBaseReviewThreadsHandler<T : DiffViewerBase>
  : GHPRDiffViewerReviewThreadsHandler {

  protected abstract val viewerReady: Boolean

  override var reviewThreadsMappings by observable<List<GHPRDiffReviewThreadMapping>>(emptyList()) { _, _, newValue ->
    if (viewerReady) updateThreads(newValue)
  }

  override var commentableRangesMappings by observable<List<GHPRDiffRangeMapping>>(emptyList()) { _, _, newValue ->
    if (viewerReady) updateCommentableRanges(newValue)
  }

  @CalledInAwt
  abstract fun updateThreads(mappings: List<GHPRDiffReviewThreadMapping>)

  @CalledInAwt
  abstract fun updateCommentableRanges(ranges: List<GHPRDiffRangeMapping>)

  override fun dispose() {}
}