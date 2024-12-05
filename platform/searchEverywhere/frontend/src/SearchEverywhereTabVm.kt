// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereTextSearchParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class SearchEverywhereTabVm(
  private val coroutineScope: CoroutineScope,
  private val providers: Collection<SearchEverywhereItemDataProvider>,
  searchPattern: StateFlow<String>,
) {
  val searchResults: StateFlow<Flow<SearchEverywhereItemData>> get() = _searchResults.asStateFlow()

  private val _searchResults: MutableStateFlow<Flow<SearchEverywhereItemData>> = MutableStateFlow(emptyFlow())
  private val isActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

  private val providerLimit: Int get() =
    if (providers.size > 1) MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT else SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT

  private val searchDispatcher: SearchEverywhereDispatcher = SearchEverywhereDispatcher(providers,
                                                                                        providers.associate { it.id to providerLimit })

  init {
    coroutineScope.launch {
      isActiveFlow.collectLatest { isActive ->
        if (!isActive) return@collectLatest

        searchPattern.mapLatest { searchPatternString ->
          val params = SearchEverywhereTextSearchParams(searchPatternString)
          searchDispatcher.getItems(params, emptyList())
        }.collect {
          _searchResults.value = it
        }
      }
    }
  }

  fun setActive(isActive: Boolean) {
    isActiveFlow.value = isActive
  }

  companion object {
    private const val SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT: Int = 30
    private const val MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT: Int = 15
  }
}