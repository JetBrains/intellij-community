// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.ide.SearchTopHitProvider
import com.intellij.ide.actions.searcheverywhere.HistoryIterator
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchHistoryList
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.SeUsageEventsLogger
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.util.SystemProperties
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SePopupVm(
  val coroutineScope: CoroutineScope,
  private val project: Project?,
  private val sessionRef: DurableRef<SeSessionEntity>,
  tabs: List<SeTab>,
  initialSearchPattern: String?,
  initialTabIndex: String,
  private val historyList: SearchHistoryList,
  private val closePopupHandler: () -> Unit,
) {
  val searchPattern: MutableStateFlow<String> = MutableStateFlow("")

  val tabVms: List<SeTabVm> = tabs.map {
    SeTabVm(project, coroutineScope, it, searchPattern)
  }

  val currentTabIndex: MutableStateFlow<Int> = MutableStateFlow(tabVms.indexOfFirst { it.tabId == initialTabIndex }.takeIf { it >= 0 } ?: 0)
  val currentTab: SeTabVm get() = tabVms[currentTabIndex.value.coerceIn(tabVms.indices)]
  val currentTabFlow: Flow<SeTabVm>

  private var historyIterator: HistoryIterator = historyList.getIterator(currentTab.tabId)
    get() {
      val selectedContributorID = currentTab.tabId
      if (field.getContributorID() != selectedContributorID) {
        field = historyList.getIterator(selectedContributorID)
      }
      return field
    }

  val usageLogger: SeUsageEventsLogger = SeUsageEventsLogger()

  var shouldLoadMore: Boolean
    get() = currentTab.shouldLoadMore
    set(value) {
      currentTab.shouldLoadMore = value
    }

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
    activeTab.setActive(true)

    searchPattern.value = initialSearchPattern ?: run {
      // History could be suppressed by the user for some reason (creating promo video, conference demo etc.)
      // or could be suppressed just for All tab in the registry.
      val suppressHistory = SystemProperties.getBooleanProperty("idea.searchEverywhere.noHistory", false) ||
                            (SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == currentTab.tabId &&
                             Registry.`is`("search.everywhere.disable.history.for.all"))
      if (!suppressHistory) historyIterator.next() else ""
    }
  }

  suspend fun itemSelected(item: SeItemData, modifiers: Int): Boolean {
    logItemSelected()
    return currentTab.itemSelected(item, modifiers, searchPattern.value)
  }

  fun selectNextTab() {
    currentTabIndex.value = (currentTabIndex.value + 1).coerceIn(tabVms.indices)
    usageLogger.tabSwitched()
  }

  fun selectPreviousTab() {
    currentTabIndex.value = (currentTabIndex.value - 1).coerceIn(tabVms.indices)
    usageLogger.tabSwitched()
  }

  fun showTab(tabId: String) {
    tabVms.indexOfFirst { it.tabId == tabId }.takeIf { it >= 0 }?.let {
      currentTabIndex.value = it
    }
  }

  private fun logItemSelected() {
    val searchText = searchPattern.value
    if (searchText.startsWith(SearchTopHitProvider.getTopHitAccelerator()) && searchText.contains(" ")) {
      usageLogger.commandUsed()
    }

    usageLogger.contributorItemSelected()
  }

  fun closePopup() {
    closePopupHandler()
  }

  fun saveSearchText() {
    val searchText = searchPattern.value
    val selectedTabID = currentTab.tabId
    if (searchText.isNotEmpty()) {
      historyList.saveText(searchText, selectedTabID)
    }
  }

  fun getHistoryItem(next: Boolean) : String? {
    val searchText = if (next) historyIterator.next() else historyIterator.prev()
    return searchText
  }

  fun getHistoryItems(): List<String> {
    return historyIterator.getList()
  }
}

private fun <T> Flow<T>.withPrevious(): Flow<Pair<T?, T>> = flow {
  var previous: T? = null
  collect { current ->
    emit(Pair(previous, current))
    previous = current
  }
}
