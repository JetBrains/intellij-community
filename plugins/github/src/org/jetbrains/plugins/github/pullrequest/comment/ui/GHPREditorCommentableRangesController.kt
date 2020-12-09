// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.diff.util.LineRange
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.ui.codereview.diff.DiffEditorGutterIconRendererFactory
import com.intellij.util.ui.codereview.diff.EditorRangesController
import org.jetbrains.plugins.github.ui.util.SingleValueModel

internal class GHPREditorCommentableRangesController(
  commentableRanges: SingleValueModel<List<LineRange>>,
  gutterIconRendererFactory: DiffEditorGutterIconRendererFactory,
  editor: EditorEx
) : EditorRangesController(gutterIconRendererFactory, editor) {

  init {
    commentableRanges.addAndInvokeValueChangedListener {
      for (range in commentableRanges.value) {
        markCommentableLines(range)
      }
    }
  }
}