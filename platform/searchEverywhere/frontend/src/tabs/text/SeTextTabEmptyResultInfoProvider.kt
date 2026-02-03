// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoChunk
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextTabEmptyResultInfoProvider(
  private val filterEditor: SeTextFilterEditor? = null,
  private val project: Project?,
) {

  fun getEmptyResultInfo(): SeEmptyResultInfo {
    val emptyInfoChunks = mutableListOf<SeEmptyResultInfoChunk>()
    emptyInfoChunks.add(SeEmptyResultInfoChunk(IdeBundle.message("searcheverywhere.nothing.found.for.all.anywhere") + "."))
    emptyInfoChunks.add(SeEmptyResultInfoChunk(text = "", onNewLine = true))

    val showResetType = showResetType()
    val showResetCase = showResetCase()
    val showResetWords = showResetWords()
    val showResetRegex = showResetRegexp()

    if (showResetCase || showResetWords || showResetRegex || showResetType) {
      emptyInfoChunks.add(SeEmptyResultInfoChunk(FindBundle.message("message.nothingFound.used.options")))
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = "", onNewLine = true))
      if (showResetCase) emptyInfoChunks.add(SeEmptyResultInfoChunk(text = FindBundle.message("find.popup.case.sensitive.label"), onNewLine = false))
      if (showResetWords) emptyInfoChunks.add(SeEmptyResultInfoChunk(text = " " + FindBundle.message("find.whole.words.label"), onNewLine = false))
      if (showResetRegex) emptyInfoChunks.add(SeEmptyResultInfoChunk(text = " " + FindBundle.message("find.regex.label"), onNewLine = false))
      if (showResetType) emptyInfoChunks.add(SeEmptyResultInfoChunk(text = " " + FindBundle.message("find.popup.filemask.label"), onNewLine = false))
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = FindBundle.message("find.popup.clear.all.options"),
                                                 onNewLine = true,
                                                 attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                 listener = { resetType(); resetCase(); resetWords(); resetRegexp() }))
    }

    if (showResetScope()) {
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = IdeBundle.message("searcheverywhere.try.to.reset.scope") +
                                                        " " + StringUtil.toLowerCase(EverythingGlobalScope.getNameText()),
                                                 onNewLine = true,
                                                 attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                 listener = { resetScope() }))
    }

    return SeEmptyResultInfo(emptyInfoChunks)
  }

  private val toggleScopeAction: SearchEverywhereToggleAction?
    get() = filterEditor?.getHeaderActions()?.firstOrNull {
      it is SearchEverywhereToggleAction
    } as? SearchEverywhereToggleAction

  // Reset text scope

  private fun showResetScope(): Boolean = toggleScopeAction?.isEverywhere == false

  private fun resetScope() {
    val action = toggleScopeAction ?: return
    action.isEverywhere = !action.isEverywhere
  }

  // Reset file type

  private fun showResetType(): Boolean = FindManager.getInstance(project).findInProjectModel.fileFilter != null

  private fun resetType() {
    filterEditor?.changeType(null)
  }

  // Reset case

  private fun showResetCase(): Boolean {
    return filterEditor?.resultFlow?.value?.let { state ->
      SeTextFilter.isCaseSensitive(state)
    } ?: return false
  }

  private fun resetCase() {
    filterEditor?.selectCaseSensitiveAction(false)
  }

  // Reset words

  private fun showResetWords(): Boolean {
    return filterEditor?.resultFlow?.value?.let { state ->
      SeTextFilter.isWholeWordsOnly(state)
    } ?: return false
  }

  private fun resetWords() {
    filterEditor?.selectWordAction(false)
  }

  // Reset regexp

  private fun showResetRegexp(): Boolean {
    return filterEditor?.resultFlow?.value?.let { state ->
      SeTextFilter.isRegularExpressions(state)
    } ?: return false
  }

  private fun resetRegexp() {
    filterEditor?.selectRegexpAction(false)
  }
}
