// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.frontend.AutoToggleAction
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfoProvider
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsFilter
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.SeDefaultTabBase
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeActionsTab(delegate: SeTabDelegate) : SeDefaultTabBase(delegate) {
  override val name: String get() = NAME
  override val id: String get() = ID
  override val isIndexingDependent: Boolean get() = true
  override val priority: Int get() = PRIORITY
  private val filterEditor: SeFilterEditor = SeActionsFilterEditor()

  override suspend fun getFilterEditor(): SeFilterEditor = filterEditor

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean = coroutineScope {
    withContext(Dispatchers.EDT) {
      delegate.itemSelected(item, modifiers, searchText)
    }
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override suspend fun getUpdatedPresentation(item: SeItemData): SeItemPresentation? {
    return delegate.getUpdatedPresentation(item)
  }

  companion object {
    @ApiStatus.Internal
    const val ID: String = "ActionSearchEverywhereContributor"
    @ApiStatus.Internal
    val NAME: String = IdeBundle.message("search.everywhere.group.name.actions")
    @ApiStatus.Internal
    const val PRIORITY: Int = 800
  }
}

private class SeActionsFilterEditor : SeFilterEditorBase<SeActionsFilter>(SeActionsFilter(false, isAutoTogglePossible = true)) {

  private val actions = listOf<AnAction>(object : CheckBoxSearchEverywhereToggleAction(IdeBundle.message("checkbox.disabled.included")), AutoToggleAction {
    private var isAutoToggleEnabled: Boolean = true

    override fun isEverywhere(): Boolean {
      return filterValue.includeDisabled
    }

    override fun setEverywhere(state: Boolean) {
      filterValue = SeActionsFilter(state, isAutoTogglePossible = false)
      isAutoToggleEnabled = false
    }

    override fun autoToggle(everywhere: Boolean): Boolean {
      if (!canToggleEverywhere() || !isAutoToggleEnabled || isEverywhere == everywhere) return false

      filterValue = SeActionsFilter(everywhere, isAutoTogglePossible = !everywhere)
      return true
    }
  })

  override fun getHeaderActions(): List<AnAction> = actions
}