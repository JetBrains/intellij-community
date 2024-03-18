// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl

import com.intellij.diff.merge.MergeThreesideViewer
import com.intellij.diff.tools.fragmented.UnifiedDiffChange
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffChange
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeys.ENABLE_SEARCH_IN_CHANGES
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.find.FindModel
import com.intellij.find.impl.livePreview.EditorSearchAreaProvider
import com.intellij.find.impl.livePreview.SearchResults.SearchArea
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx

class SearchInDiffChangesProvider : EditorSearchAreaProvider {

  override fun isEnabled(editor: Editor, findModel: FindModel): Boolean {
    if (editor.isDisposed) return false
    if (findModel.isReplaceState) return false
    val dataContext = (editor as? EditorEx)?.dataContext
    val diffContext = dataContext?.getData(DiffDataKeys.DIFF_CONTEXT) ?: return false

    return DiffUtil.isUserDataFlagSet(ENABLE_SEARCH_IN_CHANGES, diffContext)
  }

  override fun getSearchArea(editor: Editor, findModel: FindModel): SearchArea? {
    return when (val viewer = editor.getUserData(DiffUserDataKeys.DIFF_VIEWER)) {
      is SimpleDiffViewer -> {
        val side = if (viewer.editor1 == editor) Side.LEFT else Side.RIGHT
        buildSearchArea(editor.document, viewer.notSkippedChanges, { getStartLine(side) }, { getEndLine(side) })
      }
      is UnifiedDiffViewer -> buildSearchArea(editor.document, viewer.notSkippedChanges, { line1 }, { line2 })
      is MergeThreesideViewer -> buildSearchArea(editor.document, viewer.changes, { startLine }, { endLine })
      else -> null
    }
  }

  private val SimpleDiffViewer.notSkippedChanges: List<SimpleDiffChange>
    get() = diffChanges.filterNot(SimpleDiffChange::isSkipped)

  private val UnifiedDiffViewer.notSkippedChanges: List<UnifiedDiffChange>
    get() = diffChanges?.filterNot(UnifiedDiffChange::isSkipped).orEmpty()

  private fun <Change> buildSearchArea(document: Document,
                                       changes: List<Change>,
                                       startLine: Change.() -> Int,
                                       endLine: Change.() -> Int): SearchArea? {
    if (changes.isEmpty()) return null

    val starts = IntArray(changes.size)
    val ends = IntArray(changes.size)
    for (index in changes.indices) {
      val change = changes[index]

      val changeRange = DiffUtil.getLinesRange(document, change.startLine(), change.endLine())
      starts[index] = changeRange.startOffset
      ends[index] = changeRange.endOffset
    }

    return SearchArea.create(starts, ends)
  }
}
