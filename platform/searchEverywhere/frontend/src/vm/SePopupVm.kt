// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.ide.SearchTopHitProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.SeUsageEventsLogger
import com.intellij.platform.searchEverywhere.api.SeTab
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SePopupVm(val coroutineScope: CoroutineScope,
                private val project: Project,
                private val sessionRef: DurableRef<SeSessionEntity>,
                tabs: List<SeTab>,
                initialSearchPattern: String?,
                private val onClose: suspend () -> Unit) {

  val currentTabIndex: MutableStateFlow<Int> = MutableStateFlow(0)
  val currentTab: SeTabVm get() = tabVms[currentTabIndex.value.coerceIn(tabVms.indices)]

  val currentTabFlow: Flow<SeTabVm>
  val searchResults: Flow<Flow<SeListItem>>

  val searchPattern: MutableStateFlow<String> = MutableStateFlow(initialSearchPattern ?: "")

  val tabVms: List<SeTabVm> = tabs.map {
    SeTabVm(project, coroutineScope, it, searchPattern)
  }

  val usageLogger: SeUsageEventsLogger = SeUsageEventsLogger()

  var shouldLoadMore: Boolean
    get() = currentTab.shouldLoadMore
    set(value) { currentTab.shouldLoadMore = value }

  init {
    check(tabVms.isNotEmpty()) { "Search Everywhere tabs must not be empty" }

    val activeTab = tabVms.first()
    currentTabFlow = currentTabIndex.map {
      tabVms[it.coerceIn(tabVms.indices)]
    }.withPrevious().map { (prev, next) ->
      prev?.setActive(false)
      next.setActive(true)
      next
    }
    searchResults = currentTabFlow.flatMapLatest { it.searchResults }
    activeTab.setActive(true)
  }

  suspend fun itemSelected(item: SeItemData, modifiers: Int): Boolean {
    logItemSelected()
    return currentTab.itemSelected(item, modifiers, searchPattern.value)
  }

  fun  selectNextTab() {
    currentTabIndex.value = (currentTabIndex.value + 1).coerceIn(tabVms.indices)
    usageLogger.tabSwitched()
  }

  fun selectPreviousTab() {
    currentTabIndex.value = (currentTabIndex.value - 1).coerceIn(tabVms.indices)
    usageLogger.tabSwitched()
  }

  fun dispose() {
    coroutineScope.launch {
      onClose()
    }
  }

  private fun logItemSelected() {
    val searchText = searchPattern.value
    if (searchText.startsWith(SearchTopHitProvider.getTopHitAccelerator()) && searchText.contains(" ")) {
      usageLogger.commandUsed()
    }

    usageLogger.contributorItemSelected()
  }
}

private fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> = flow {
  var previous: T? = null
  collect { current ->
    emit(Pair(previous, current))
    previous = current
  }
}
