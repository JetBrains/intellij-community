// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereTabProvider
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SearchEverywherePopupVm(val coroutineScope: CoroutineScope,
                              private val project: Project,
                              private val sessionId: EID,
                              private val onClose: suspend () -> Unit) {

  val currentTab: StateFlow<SearchEverywhereTabVm>
  val searchResults: Flow<Flow<SearchEverywhereItemData>>

  val searchPattern = MutableStateFlow("")

  val tabVms: List<SearchEverywhereTabVm> = SearchEverywhereTabProvider.EP_NAME.extensionList.map {
    val tab = it.getTab(project, sessionId)
    SearchEverywhereTabVm(coroutineScope, tab, searchPattern)
  }

  private val _currentTab: MutableStateFlow<SearchEverywhereTabVm>

  init {
    check(tabVms.isNotEmpty()) { "Search Everywhere tabs must not be empty" }

    val activeTab = tabVms.first()
    _currentTab = MutableStateFlow(activeTab)
    currentTab = _currentTab.asStateFlow()
    searchResults = currentTab.flatMapLatest { it.searchResults }
    activeTab.setActive(true)
  }

  fun selectTab(index: Int) {
    _currentTab.value.setActive(false)
    _currentTab.value = tabVms[index.coerceIn(0..<tabVms.size)]
    _currentTab.value.setActive(true)
  }

  fun dispose() {
    coroutineScope.launch {
      onClose()
    }
  }
}