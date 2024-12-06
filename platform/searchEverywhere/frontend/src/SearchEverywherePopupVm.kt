// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SearchEverywherePopupVm(private val coroutineScope: CoroutineScope,
                              tabs: List<SearchEverywhereTab>,
                              providers: Map<SearchEverywhereProviderId, SearchEverywhereItemDataProvider>) {

  val currentTab: StateFlow<SearchEverywhereTabVm> get() = _currentTab.asStateFlow()
  val searchResults: Flow<Flow<SearchEverywhereItemData>> = currentTab.flatMapLatest { it.searchResults }

  private val searchPattern = MutableStateFlow("")

  private val tabVms: List<SearchEverywhereTabVm> = tabs.mapNotNull { tab ->
    val tabProviders = tab.providers.map {
      providers[SearchEverywhereProviderId(it)]!!
    }.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

    SearchEverywhereTabVm(coroutineScope, tabProviders, searchPattern)
  }

  private val _currentTab: MutableStateFlow<SearchEverywhereTabVm>

  init {
    check(tabs.isNotEmpty()) {
      "Search Everywhere tabs must not be empty"
    }

    _currentTab = MutableStateFlow(tabVms.first())
  }

  fun selectTab(index: Int) {
    _currentTab.value = tabVms[index.coerceIn(0..<tabVms.size)]
  }

  fun setSearchPattern(pattern: String) {
    searchPattern.value = pattern
  }
}