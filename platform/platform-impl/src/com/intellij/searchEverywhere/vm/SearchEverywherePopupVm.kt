// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.vm

import com.intellij.openapi.project.Project
import com.intellij.searchEverywhere.core.DefaultSearchEverywhereRequestHandler
import com.intellij.searchEverywhere.core.SearchEverywhereTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywherePopupVm(private val coroutineScope: CoroutineScope,
                              private val project: Project,
                              private val tabs: List<SearchEverywhereTab>) {

  val currentTab: StateFlow<SearchEverywhereTabVm> get() = _currentTab.asStateFlow()

  private val searchPattern = MutableStateFlow("")
  private val tabVms: List<SearchEverywhereTabVm> = tabs.map {
    SearchEverywhereTabVm(coroutineScope, it, searchPattern, DefaultSearchEverywhereRequestHandler())
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