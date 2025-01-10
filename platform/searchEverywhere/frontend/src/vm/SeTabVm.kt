// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.openapi.options.ObservableOptionEditor
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeTextSearchParams
import com.intellij.platform.searchEverywhere.api.SeFilterData
import com.intellij.platform.searchEverywhere.api.SeTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class SeTabVm(
  coroutineScope: CoroutineScope,
  private val tab: SeTab,
  searchPattern: StateFlow<String>,
) {
  val searchResults: StateFlow<Flow<SeItemData>> get() = _searchResults.asStateFlow()
  val name: String get() = tab.name
  val filterEditor: ObservableOptionEditor<SeFilterData>? = tab.getFilterEditor()

  private val _searchResults: MutableStateFlow<Flow<SeItemData>> = MutableStateFlow(emptyFlow())
  private val isActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

  init {
    coroutineScope.launch {
      isActiveFlow.collectLatest { isActive ->
        if (!isActive) return@collectLatest

        combine(searchPattern, filterEditor?.resultFlow ?: flowOf(null)) { searchPattern, filterData ->
          Pair(searchPattern, filterData)
        }.mapLatest { (searchPattern, filterData) ->
          val params = SeTextSearchParams(searchPattern, filterData)
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