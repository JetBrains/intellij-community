// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeFilterActionsPresentation
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeFilterPresentation
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionsFilter
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeActionsTab(private val delegate: SeTabDelegate): SeTab {
  override val name: String get() = IdeBundle.message("search.everywhere.group.name.actions")
  override val shortName: String get() = name
  override val id: String get() = "ActionSearchEverywhereContributor"

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)
  override fun getFilterEditor(): SeFilterEditor = SeActionsFilterEditor()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    val presentation = item.presentation
    if (presentation is SeActionItemPresentation) {
      presentation.commonData.toggleStateIfSwitcher()
    }

    return delegate.itemSelected(item, modifiers, searchText)
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
