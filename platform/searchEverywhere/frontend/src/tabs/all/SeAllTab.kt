// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.all

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.*
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import com.intellij.ui.IdeUICustomization
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeAllTab(private val delegate: SeTabDelegate): SeTab {
  override val name: String
    get() = IdeBundle.message("searcheverywhere.all.elements.tab.name")

  override val shortName: String
    get() = name

  override val id: String get() = ID

  override fun getItems(params: SeParams): Flow<SeResultEvent> = delegate.getItems(params)
  override fun getFilterEditor(): SeFilterEditor? = SeAllFilterEditor()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }

  companion object {
    @ApiStatus.Internal
    const val ID: String = SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
  }
}

private class SeAllFilterEditor : SeFilterEditorBase<SeEverywhereFilter>(SeEverywhereFilter(false)) {
  override fun getPresentation(): SeFilterPresentation {
    return object : SeFilterActionsPresentation {
      override fun getActions(): List<AnAction> = listOf<AnAction>(object : CheckBoxSearchEverywhereToggleAction(
        IdeUICustomization.getInstance().projectMessage("checkbox.include.non.project.items")
      ) {
        override fun isEverywhere(): Boolean {
          return filterValue.isEverywhere
        }

        override fun setEverywhere(state: Boolean) {
          filterValue = SeEverywhereFilter(state)
        }
      });
    }
  }
}
