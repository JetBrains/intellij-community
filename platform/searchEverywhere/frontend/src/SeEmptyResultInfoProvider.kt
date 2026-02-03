// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.isText
import com.intellij.platform.searchEverywhere.isWildcard
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionListener

@ApiStatus.Internal
class SeEmptyResultInfoProvider(
  private val filterEditor: SeFilterEditor? = null,
  private val providersIds: List<SeProviderId>,
  private val canBeShownInFindResults: Boolean,
) {

  fun getEmptyResultInfo(
    project: Project?,
    context: DataContext,
  ): SeEmptyResultInfo {
    val emptyInfoChunks = mutableListOf<SeEmptyResultInfoChunk>()

    val showResetScope = showResetScope()
    if (providersIds.any { it.isWildcard || it.isText }) {

      emptyInfoChunks.add(SeEmptyResultInfoChunk(IdeBundle.message("searcheverywhere.nothing.found.for.all.anywhere") + "."))

      if (showResetScope) {
        emptyInfoChunks.add(SeEmptyResultInfoChunk(text = "", onNewLine = true))
        emptyInfoChunks.add(SeEmptyResultInfoChunk(text = IdeBundle.message("searcheverywhere.try.to.reset.scope") +
                                                          " " + StringUtil.toLowerCase(EverythingGlobalScope.getNameText()),
                                                   onNewLine = true,
                                                   attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                   listener = { resetScope() }))
      }
      return SeEmptyResultInfo(emptyInfoChunks)
    }

    val showFindInFilesAction = canBeShownInFindResults
    val showResetFilter = filtersAction?.isActive ?: false
    val anyActionAllowed = showResetScope || showFindInFilesAction || showResetFilter

    var firstPartAdded = false
    var actionsPrinted = 0
    if (showResetScope) {
      val resetScopeListener = ActionListener { resetScope() }
      emptyInfoChunks.add(SeEmptyResultInfoChunk(IdeBundle.message("searcheverywhere.try.to.reset.scope")))
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = " " + StringUtil.toLowerCase(EverythingGlobalScope.getNameText()),
                                                 attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                 listener = resetScopeListener))
      firstPartAdded = true
      actionsPrinted++
    }

    if (showResetFilter) {
      val clearFiltersAction = ActionListener { selectAllFilterElements() }
      if (firstPartAdded) emptyInfoChunks.add(SeEmptyResultInfoChunk(", "))
      val resetFilterMessage = IdeBundle.message("searcheverywhere.reset.filters")
      emptyInfoChunks.add(SeEmptyResultInfoChunk(text = if (firstPartAdded) Strings.toLowerCase(resetFilterMessage) else resetFilterMessage,
                                                 attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES,
                                                 listener = clearFiltersAction))
      firstPartAdded = true

      actionsPrinted++
      if (actionsPrinted >= 2) {
        emptyInfoChunks.add(SeEmptyResultInfoChunk(text = "", onNewLine = true))
        actionsPrinted = 0
      }
    }

    if (showFindInFilesAction) {
      val manager = FindInProjectManager.getInstance(project)
      if (manager != null && manager.isEnabled) {
        val findInFilesAction = ActionListener { manager.findInProject(context, null) }
        emptyInfoChunks.add(SeEmptyResultInfoChunk((if (firstPartAdded) " " + IdeBundle.message("searcheverywhere.use.optional")
        else IdeBundle.message("searcheverywhere.use.main")) + " "))
        emptyInfoChunks.add(SeEmptyResultInfoChunk(text = IdeBundle.message("searcheverywhere.try.to.find.in.files"),
                                                   attrs = SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES, listener = findInFilesAction))
        val findInFilesShortcut = KeymapUtil.getFirstKeyboardShortcutText("FindInPath")
        if (!StringUtil.isEmpty(findInFilesShortcut)) {
          emptyInfoChunks.add(SeEmptyResultInfoChunk(" ($findInFilesShortcut)"))
        }

        actionsPrinted++
        if (actionsPrinted >= 2) {
          emptyInfoChunks.add(SeEmptyResultInfoChunk(text = "", onNewLine = true))
        }

        emptyInfoChunks.add(SeEmptyResultInfoChunk(" " + IdeBundle.message("searcheverywhere.to.perform.fulltext.search")))
      }
    }

    if (anyActionAllowed) {
      emptyInfoChunks.add(SeEmptyResultInfoChunk("."))
    }

    return SeEmptyResultInfo(emptyInfoChunks)
  }

  private val toggleAction: SearchEverywhereToggleAction?
    get() = filterEditor?.getHeaderActions()?.firstOrNull {
      it is SearchEverywhereToggleAction
    } as? SearchEverywhereToggleAction

  private fun showResetScope(): Boolean = toggleAction?.isEverywhere == false

  private fun resetScope() {
    val action = toggleAction ?: return
    action.isEverywhere = !action.isEverywhere
  }

  private val filtersAction: SearchEverywhereFiltersAction<*>? =
    filterEditor?.getHeaderActions()?.find { it is SearchEverywhereFiltersAction<*> }
      ?.let { it as? SearchEverywhereFiltersAction<*> }

  private val filter: PersistentSearchEverywhereContributorFilter<*>? = filtersAction?.filter

  private fun selectAllFilterElements() {
    filter?.setSelectedToAllElements(true)
  }
}