// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import com.intellij.diff.util.LineRange

data class GHPRReviewEditorGutterControlsState(
  override val linesWithComments: Set<Int>,
  override val linesWithNewComments: Set<Int>,
  val commentableLines: List<LineRange>
) : CodeReviewEditorGutterControlsModel.ControlsState {
  override fun isLineCommentable(lineIdx: Int): Boolean = commentableLines.any {
    val end = if (it.start == it.end) it.end.inc() else it.end
    lineIdx in it.start until end
  }
}