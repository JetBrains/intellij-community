// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.all

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.CheckBoxSearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFiltersAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.util.gotoByName.SearchEverywhereConfiguration
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.*
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.frontend.tabs.utils.SeFilterEditorBase
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import com.intellij.ui.IdeUICustomization
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Function

@ApiStatus.Internal
class SeAllTab(private val delegate: SeTabDelegate) : SeTab {
  override val name: String
    get() = IdeBundle.message("searcheverywhere.all.elements.tab.name")

  override val shortName: String
    get() = name

  override val isIndexingDependent: Boolean get() = true

  override val id: String get() = ID
  private val filterEditor: SuspendLazyProperty<SeFilterEditor> = initAsync(delegate.scope) {
    SeAllFilterEditor(delegate.getProvidersIdToName())
  }

  override suspend fun essentialProviderIds(): Set<SeProviderId> = delegate.essentialProviderIds()

  override fun getItems(params: SeParams): Flow<SeResultEvent> {
    val allTabFilter = SeEverywhereFilter.from(params.filter)
    return delegate.getItems(params, allTabFilter.disabledProviderIds)
  }

  override suspend fun getFilterEditor(): SeFilterEditor = filterEditor.getValue()

  override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return delegate.itemSelected(item, modifiers, searchText)
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo {
    return SeEmptyResultInfoProvider(getFilterEditor(),
                                     delegate.getProvidersIds(),
                                     delegate.canBeShownInFindResults()).getEmptyResultInfo(delegate.project, context)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return delegate.canBeShownInFindResults()
  }

  override suspend fun openInFindToolWindow(sessionRef: DurableRef<SeSessionEntity>, params: SeParams, initEvent: AnActionEvent): Boolean {
    val allTabFilter = SeEverywhereFilter.from(params.filter)
    return delegate.openInFindToolWindow(sessionRef, params, initEvent, true,allTabFilter.disabledProviderIds)
  }

  override fun dispose() {
    Disposer.dispose(delegate)
  }

  companion object {
    @ApiStatus.Internal
    const val ID: String = SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
  }
}

private class SeAllFilterEditor(providersIdToName: Map<SeProviderId, @Nls String>) : SeFilterEditorBase<SeEverywhereFilter>(SeEverywhereFilter(false, disabledProviders)) {
  private val actions = listOf(getEverywhereToggleAction(), getFilterTypesAction(providersIdToName))
  override fun getActions(): List<AnAction> = actions

  private fun getEverywhereToggleAction() = object : CheckBoxSearchEverywhereToggleAction(IdeUICustomization.getInstance().projectMessage("checkbox.include.non.project.items")), AutoToggleAction {
    private var isAutoToggleEnabled: Boolean = true

    override fun isEverywhere(): Boolean {
      return filterValue.isEverywhere
    }

    override fun setEverywhere(state: Boolean) {
      filterValue = filterValue.cloneWith(state)
      isAutoToggleEnabled = false
    }

    override fun autoToggle(everywhere: Boolean): Boolean {
      if (!canToggleEverywhere() || !isAutoToggleEnabled || isEverywhere == everywhere) return false

      filterValue = filterValue.cloneWith(everywhere)
      return true
    }
  }

  private fun getFilterTypesAction(providersIdToName: Map<SeProviderId, @Nls String>): AnAction {
    val namesMap = providersIdToName.mapNotNull {
      // Workaround for: IJPL-188383 Search Everywhere, All tab: 'Top Hit' filter is duplicated
      // Don't add 'Top Hit (On Client)' to the list of providers
      if (it.key.value == SeProviderIdUtils.TOP_HIT_ID) return@mapNotNull null

      it.key.value to it.value
    }.toMap()

    val configuration = SearchEverywhereConfiguration.getInstance()
    val persistentFilter =
      PersistentSearchEverywhereContributorFilter(namesMap.keys.toList().sortedWith { a, b -> namesMap[a]!!.compareTo(namesMap[b]!!) },
                                                  configuration,
                                                  Function { key: String? -> namesMap[key] },
                                                  Function { null })

    return SearchEverywhereFiltersAction(persistentFilter) {
      filterValue = filterValue.cloneWith(disabledProviders)
    }
  }

  companion object {
    val disabledProviders: List<SeProviderId>
      get() =
        SearchEverywhereConfiguration.getInstance().state?.filteredOutFileTypeNames?.map { SeProviderId(it) } ?: emptyList()
  }
}
