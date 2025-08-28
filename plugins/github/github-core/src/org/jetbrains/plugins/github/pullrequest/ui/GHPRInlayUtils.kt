// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.collaboration.async.combineState
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.diff.DiffLineLocation
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.pullrequest.ui.comment.CommentedCodeFrameRenderer
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPREditorMappedComponentModel

internal object GHPRInlayUtils {
  internal fun installInlayHoverOutline(
    parentCs: CoroutineScope,
    editor: Editor,
    side: Side?,
    locationToLine: ((DiffLineLocation) -> Int?)?,
    vm: GHPREditorMappedComponentModel,
  ) {
    val cs: CoroutineScope = parentCs.childScope("Comment inlay hover controller")
    var activeLineHighlighter: RangeHighlighter? = null

    cs.launchNow {
      vm.shouldShowOutline.combineState(vm.range, ::Pair).collectLatest { (shouldShowOutline, _) ->
        if (!shouldShowOutline) {
          activeLineHighlighter?.let { editor.markupModel.removeHighlighter(it) }
          activeLineHighlighter = null
          return@collectLatest
        }
        val inlayCommentRange = vm.range.value
        if (inlayCommentRange == null) return@collectLatest
        val commentRange = if (locationToLine == null) {
          inlayCommentRange.second
        }
        else {
          val startLine = locationToLine(inlayCommentRange.first to inlayCommentRange.second.first) ?: return@collectLatest
          val endLine = locationToLine(inlayCommentRange.first to inlayCommentRange.second.last) ?: return@collectLatest
          startLine..endLine
        }
        val startOffset = editor.document.getLineStartOffset(commentRange.first)
        val endOffset = editor.document.getLineEndOffset(commentRange.last)
        val renderer = CommentedCodeFrameRenderer(commentRange.first, commentRange.last, side)
        activeLineHighlighter = editor.markupModel.addRangeHighlighter(startOffset, endOffset, HighlighterLayer.LAST, null, HighlighterTargetArea.LINES_IN_RANGE).also { highlighter ->
          highlighter.customRenderer = renderer
          highlighter.lineMarkerRenderer = renderer
        }
      }
    }
    cs.launch {
      try {
        awaitCancellation()
      }
      finally {
        vm.showOutline(false)
        activeLineHighlighter?.let { editor.markupModel.removeHighlighter(it) }
        activeLineHighlighter = null
      }
    }
  }

}