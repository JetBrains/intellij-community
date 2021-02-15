// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.viewer

import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.util.Range
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewThreadMapping
import org.jetbrains.plugins.github.ui.util.SingleValueModel

abstract class GHPRDiffViewerBaseReviewThreadsHandler<T : DiffViewerBase>(private val commentableRangesModel: SingleValueModel<List<Range>?>,
                                                                          private val reviewThreadsModel: SingleValueModel<List<GHPRDiffReviewThreadMapping>?>,
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

  @RequiresEdt
  abstract fun markCommentableRanges(ranges: List<Range>?)

  @RequiresEdt
  abstract fun showThreads(threads: List<GHPRDiffReviewThreadMapping>?)

  companion object {
    internal fun getCommentLinesRange(editor: EditorEx, line: Int): Pair<Int, Int> {
      if (!editor.selectionModel.hasSelection()) return line to line

      return with(editor.selectionModel) {
        editor.offsetToLogicalPosition(selectionStart).line to editor.offsetToLogicalPosition(selectionEnd).line
      }
    }
  }
}
