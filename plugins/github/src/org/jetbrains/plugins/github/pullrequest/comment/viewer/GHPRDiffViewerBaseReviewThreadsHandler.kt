// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.Range
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread
import org.jetbrains.plugins.github.ui.util.SingleValueModel

abstract class GHPRDiffViewerBaseReviewThreadsHandler<T : DiffViewerBase>(private val commentableRangesModel: SingleValueModel<List<Range>?>,
                                                                          private val reviewThreadsModel: SingleValueModel<List<GHPullRequestReviewThread>?>,
                                                                          protected val viewer: T) {

  protected abstract val viewerReady: Boolean

  init {
    update()
    viewer.addListener(object : DiffViewerListener() {
      override fun onAfterRediff() {
        update()
      }
    })
    commentableRangesModel.addValueChangedListener {
      if (viewerReady) {
        markCommentableRanges(commentableRangesModel.value)
      }
    }
    reviewThreadsModel.addValueChangedListener {
      if (viewerReady) {
        showThreads(reviewThreadsModel.value)
      }
    }
  }

  private fun update() {
    if (viewerReady) {
      markCommentableRanges(commentableRangesModel.value)
      showThreads(reviewThreadsModel.value)
    }
  }

  @CalledInAwt
  abstract fun markCommentableRanges(ranges: List<Range>?)

  @CalledInAwt
  abstract fun showThreads(threads: List<GHPullRequestReviewThread>?)
}