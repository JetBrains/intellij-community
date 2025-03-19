// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUserDataKeys.ENABLE_SEARCH_IN_CHANGES
import com.intellij.diff.util.DiffUtil
import com.intellij.find.FindModel
import com.intellij.find.impl.livePreview.EditorSearchAreaProvider
import com.intellij.find.impl.livePreview.SearchResults.SearchArea
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx

internal class SearchInDiffChangesProvider : EditorSearchAreaProvider {

  override fun isEnabled(editor: Editor, findModel: FindModel): Boolean {
    if (editor.isDisposed) return false
    if (findModel.isReplaceState) return false
    val dataContext = (editor as? EditorEx)?.dataContext ?: return false

    val diffContext = dataContext.getData(DiffDataKeys.DIFF_CONTEXT)
    val changedRangeProvider = dataContext.getData(DiffDataKeys.EDITOR_CHANGED_RANGE_PROVIDER)
    return diffContext != null && changedRangeProvider != null &&
           DiffUtil.isUserDataFlagSet(ENABLE_SEARCH_IN_CHANGES, diffContext)
  }

  override fun getSearchArea(editor: Editor, findModel: FindModel): SearchArea? {
    val dataContext = (editor as? EditorEx)?.dataContext ?: return null
    val changedRangeProvider = dataContext.getData(DiffDataKeys.EDITOR_CHANGED_RANGE_PROVIDER) ?: return null

    val changedRanges = changedRangeProvider.getChangedRanges(editor) ?: return null
    val starts = IntArray(changedRanges.size)
    val ends = IntArray(changedRanges.size)
    changedRanges.forEachIndexed { index, range ->
      starts[index] = range.startOffset
      ends[index] = range.endOffset
    }
    return SearchArea.create(starts, ends)
  }
}
