// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import com.intellij.platform.searchEverywhere.SearchEverywhereTextSearchParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class SearchEverywhereTabVm(
  coroutineScope: CoroutineScope,
  private val tab: SearchEverywhereTab,
  searchPattern: StateFlow<String>,
) {
  val searchResults: StateFlow<Flow<SearchEverywhereItemData>> get() = _searchResults.asStateFlow()
  val name: String get() = tab.name

  private val _searchResults: MutableStateFlow<Flow<SearchEverywhereItemData>> = MutableStateFlow(emptyFlow())
  private val isActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

  init {
    coroutineScope.launch {
      isActiveFlow.collectLatest { isActive ->
        if (!isActive) return@collectLatest

        searchPattern.mapLatest { searchPatternString ->
          val params = SearchEverywhereTextSearchParams(searchPatternString)
          tab.getItems(params)
        }.collect {
          _searchResults.value = it
        }
      }
    }
  }

  fun setActive(isActive: Boolean) {
    isActiveFlow.value = isActive
  }
}