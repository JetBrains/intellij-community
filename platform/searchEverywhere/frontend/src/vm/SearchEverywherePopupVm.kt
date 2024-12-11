// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereTabProvider
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SearchEverywherePopupVm(private val coroutineScope: CoroutineScope, private val project: Project, private val sessionId: EID, onClose: suspend () -> Unit) {

  val currentTab: StateFlow<SearchEverywhereTabVm> get() = _currentTab.asStateFlow()
  val searchResults: Flow<Flow<SearchEverywhereItemData>> = currentTab.flatMapLatest { it.searchResults }

  private val searchPattern = MutableStateFlow("")

  private val tabVms: List<SearchEverywhereTabVm> = SearchEverywhereTabProvider.EP_NAME.extensionList.map {
    val tab = it.getTab(project, sessionId)
    SearchEverywhereTabVm(coroutineScope, tab, searchPattern)
  }

  private val _currentTab: MutableStateFlow<SearchEverywhereTabVm>

  init {
    check(tabVms.isNotEmpty()) { "Search Everywhere tabs must not be empty" }
    _currentTab = MutableStateFlow(tabVms.first())
  }

  fun selectTab(index: Int) {
    _currentTab.value = tabVms[index.coerceIn(0..<tabVms.size)]
  }

  fun setSearchPattern(pattern: String) {
    searchPattern.value = pattern
  }
}