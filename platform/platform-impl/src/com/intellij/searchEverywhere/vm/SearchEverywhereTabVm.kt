// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.vm

import com.intellij.searchEverywhere.SearchEverywhereListItem
import com.intellij.searchEverywhere.core.SearchEverywhereRequestHandler
import com.intellij.searchEverywhere.core.SearchEverywhereTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SearchEverywhereTabVm(
  private val coroutineScope: CoroutineScope,
  private val info: SearchEverywhereTab,
  searchPattern: StateFlow<String>,
  requestsHandler: SearchEverywhereRequestHandler,
) {
  val searchResults: StateFlow<List<SearchEverywhereListItem<*, *>>> get() = _searchResults.asStateFlow()

  private val _searchResults: MutableStateFlow<List<SearchEverywhereListItem<*, *>>> = MutableStateFlow(emptyList())
  private val isActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

  private val providerLimit: Int get() =
    if (info.providers.size > 1) MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT else SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT

  init {
    coroutineScope.launch {
      isActiveFlow.collectLatest { isActive ->
        if (!isActive) return@collectLatest

        searchPattern.flatMapLatest { searchPatternString ->
          requestsHandler.search(
            info.providers,
            searchPatternString,
            providerLimit,
            emptyList()
          )
        }.collectLatest {
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