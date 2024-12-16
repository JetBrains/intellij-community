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

  val currentTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)

  val currentTab: Flow<SearchEverywhereTabVm>
  val searchResults: Flow<Flow<SearchEverywhereItemData>>

  val searchPattern = MutableStateFlow("")

  val tabVms: List<SearchEverywhereTabVm> = SearchEverywhereTabProvider.EP_NAME.extensionList.map {
    val tab = it.getTab(project, sessionId)
    SearchEverywhereTabVm(coroutineScope, tab, searchPattern)
  }

  init {
    check(tabVms.isNotEmpty()) { "Search Everywhere tabs must not be empty" }

    val activeTab = tabVms.first()
    currentTab = currentTabIndex.map {
      println("ayay currentTabIndex $it")
      tabVms[it.coerceIn(tabVms.indices)]
    }.withPrevious().map { (prev, next) ->
      println("ayay prev-next ${prev?.name} -> ${next.name}")
      prev?.setActive(false)
      next.setActive(true)
      next
    }
    searchResults = currentTab.flatMapLatest { it.searchResults }
    activeTab.setActive(true)
  }

  fun dispose() {
    coroutineScope.launch {
      onClose()
    }
  }
}

private fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> = flow {
  var previous: T? = null
  collect { current ->
    emit(Pair(previous, current))
    previous = current
  }
}
