// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.vm

import com.intellij.searchEverywhere.SearchEverywhereViewItem
import com.intellij.searchEverywhere.core.SearchEverywhereDispatcher
import com.intellij.searchEverywhere.core.SearchEverywhereTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SearchEverywhereTabVm(
  private val coroutineScope: CoroutineScope,
  private val info: SearchEverywhereTab,
  searchPattern: StateFlow<String>,
  searchDispatcher: SearchEverywhereDispatcher,
) {
  val searchResults: StateFlow<List<SearchEverywhereViewItem<*, *>>> get() = _searchResults.asStateFlow()

  private val _searchResults: MutableStateFlow<List<SearchEverywhereViewItem<*, *>>> = MutableStateFlow(emptyList())
  private val isActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

  private val providerLimit: Int get() =
    if (info.providers.size > 1) MULTIPLE_CONTRIBUTORS_ELEMENTS_LIMIT else SINGLE_CONTRIBUTOR_ELEMENTS_LIMIT

  init {
    coroutineScope.launch {
      isActiveFlow.collectLatest { isActive ->
        if (!isActive) return@collectLatest

        //searchPattern.flatMapLatest { searchPatternString ->
        //  searchDispatcher.search(
        //    coroutineScope,
        //    info.providers,
        //    searchPatternString,
        //    providerLimit,
        //    emptyList()
        //  ) {
        //    true
        //  }
        //}.collectLatest {
        //  _searchResults.value = it
        //}
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