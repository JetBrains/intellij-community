// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.*
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsFilter
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeActionsTab(private val delegate: SeTabDelegate): SeTab {
  override val name: String get() = IdeBundle.message("search.everywhere.group.name.actions")
  override val shortName: String get() = name
  override val id: String get() = "ActionSearchEverywhereContributor"
  private val filterEditor: SeFilterEditor = SeActionsFilterEditor()

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)
  override suspend fun getFilterEditor(): SeFilterEditor = filterEditor

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean = coroutineScope {
    withContext(Dispatchers.EDT) {
      val presentation = item.presentation
      if (presentation is SeActionItemPresentation) {
        presentation.commonData.toggleStateIfSwitcher()
      }

      delegate.itemSelected(item, modifiers, searchText)
    }
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo? {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }
}

private class SeActionsFilterEditor : SeFilterEditorBase<SeActionsFilter>(SeActionsFilter(false)) {
  override fun getPresentation(): SeFilterPresentation {
    return object : SeFilterActionsPresentation {
      override fun getActions(): List<AnAction> {
        return listOf<AnAction>(object : CheckBoxSearchEverywhereToggleAction(IdeBundle.message("checkbox.disabled.included")) {
          override fun isEverywhere(): Boolean {
            return filterValue.includeDisabled
          }

          override fun setEverywhere(state: Boolean) {
            filterValue = SeActionsFilter(state)
          }
        })
      }
    }
  }
}
