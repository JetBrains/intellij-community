// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywherePopupVm(private val coroutineScope: CoroutineScope,
                              private val tabs: List<SearchEverywhereTab>,
                              private val providers: Map<SearchEverywhereTab, Collection<SearchEverywhereItemDataProvider>>) {

  val currentTab: StateFlow<SearchEverywhereTabVm> get() = _currentTab.asStateFlow()

  private val searchPattern = MutableStateFlow("")
  private val tabVms: List<SearchEverywhereTabVm> = tabs.map {
    SearchEverywhereTabVm(coroutineScope, providers[it]!!, searchPattern)
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