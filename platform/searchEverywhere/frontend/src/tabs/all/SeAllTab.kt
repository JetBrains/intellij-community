// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.all

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.util.gotoByName.SearchEverywhereConfiguration
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeFilterActionsPresentation
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeFilterPresentation
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import com.intellij.ui.IdeUICustomization
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Function

@ApiStatus.Internal
class SeAllTab(private val delegate: SeTabDelegate): SeTab {
  override val name: String
    get() = IdeBundle.message("searcheverywhere.all.elements.tab.name")

  override val shortName: String
    get() = name

  override val id: String get() = ID

  override fun getItems(params: SeParams): Flow<SeResultEvent> {
    if (params.inputQuery.isEmpty()) return emptyFlow()

    val allTabFilter = SeEverywhereFilter.from(params.filter)
    return delegate.getItems(params, allTabFilter.disabledProviderIds)
  }

  override suspend fun getFilterEditor(): SeFilterEditor? = SeAllFilterEditor(delegate.getProvidersIdToName())

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

private class SeAllFilterEditor(private val providersIdToName: Map<SeProviderId, @Nls String>) : SeFilterEditorBase<SeEverywhereFilter>(SeEverywhereFilter(false, disabledProviders)) {
  override fun getPresentation(): SeFilterPresentation {
    return object : SeFilterActionsPresentation {
      override fun getActions(): List<AnAction> = listOf(getEverywhereToggleAction(), getFilterTypesAction(providersIdToName))
    }
  }

  private fun getEverywhereToggleAction() = object : CheckBoxSearchEverywhereToggleAction(IdeUICustomization.getInstance().projectMessage("checkbox.include.non.project.items")) {
    override fun isEverywhere(): Boolean {
      return filterValue.isEverywhere
    }

    override fun setEverywhere(state: Boolean) {
      filterValue = filterValue.cloneWith(state)
    }
  }

  private fun getFilterTypesAction(providersIdToName: Map<SeProviderId, @Nls String>): AnAction {
    val namesMap = providersIdToName.map { it.key.value to it.value }.toMap()

    val configuration = SearchEverywhereConfiguration.getInstance()
    val persistentFilter =
      PersistentSearchEverywhereContributorFilter(namesMap.keys.toList().sortedWith { a, b -> namesMap[a]!!.compareTo(namesMap[b]!!) },
                                                  configuration,
                                                  Function { key: String? -> namesMap[key] },
                                                  Function { c: String? -> null })

    return SearchEverywhereFiltersAction(persistentFilter) {
      filterValue = filterValue.cloneWith(disabledProviders)
    }
  }

  companion object {
    val disabledProviders: List<SeProviderId> get() =
      SearchEverywhereConfiguration.getInstance().state?.filteredOutFileTypeNames?.map { SeProviderId(it) } ?: emptyList()
  }
}
