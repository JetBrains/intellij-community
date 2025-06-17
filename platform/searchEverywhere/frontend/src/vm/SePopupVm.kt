// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.HistoryIterator
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchHistoryList
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.util.SystemProperties
import fleet.kernel.DurableRef
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SePopupVm(
  val coroutineScope: CoroutineScope,
  private val project: Project?,
  private val sessionRef: DurableRef<SeSessionEntity>,
  tabs: List<SeTab>,
  deferredTabs: List<SuspendLazyProperty<SeTab?>>,
  initialSearchPattern: String?,
  initialTabIndex: String,
  private val historyList: SearchHistoryList,
  private val closePopupHandler: () -> Unit,
) {
  val searchPattern: MutableStateFlow<String> = MutableStateFlow("")

  private val _deferredTabVms = MutableSharedFlow<SeTabVm>(replay = 100)
  val deferredTabVms: SharedFlow<SeTabVm> = _deferredTabVms.asSharedFlow()
  private val tabVmsSateFlow = MutableStateFlow(tabs.map { SeTabVm(project, coroutineScope, it, searchPattern) })
  val tabVms: List<SeTabVm> get() = tabVmsSateFlow.value

  val currentTabIndex: MutableStateFlow<Int> = MutableStateFlow(tabVms.indexOfFirst { it.tabId == initialTabIndex }.takeIf { it >= 0 } ?: 0)
  val currentTab: SeTabVm get() = tabVms[currentTabIndex.value.coerceIn(tabVms.indices)]
  val currentTabFlow: Flow<SeTabVm>

  private val canBeShownInFindResultsFlow = MutableStateFlow(false)
  val canBeShownInFindResults: Boolean get() = canBeShownInFindResultsFlow.value

  private var historyIterator: HistoryIterator = historyList.getIterator(currentTab.tabId)
    get() {
      val selectedContributorID = currentTab.tabId
      if (field.getContributorID() != selectedContributorID) {
        field = historyList.getIterator(selectedContributorID)
      }
      return field
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

    coroutineScope.launch {
      deferredTabVms.collect { tabVm ->
        tabVmsSateFlow.update { it + tabVm }
      }
    }

    coroutineScope.launch {
      currentTabFlow.collect { tabVm ->
        canBeShownInFindResultsFlow.update { tabVm.canBeShownInFindResults() }
      }
    }

    deferredTabs.forEach {
      coroutineScope.launch {
        it.getValue()?.let { tab ->
          _deferredTabVms.emit(SeTabVm(project, coroutineScope, tab, searchPattern))
        }
      }
    }
  }

  suspend fun itemsSelected(indexedItems: List<Pair<Int, SeItemData>>, areIndexesOriginal: Boolean, modifiers: Int): Boolean {
    val currentTab = currentTab

    return coroutineScope {
      indexedItems.map { item ->
        async {
          currentTab.itemSelected(item, areIndexesOriginal, modifiers, searchPattern.value)
        }
      }.awaitAll().any { it }
    }
  }

  suspend fun openInFindWindow(sessionRef: DurableRef<SeSessionEntity>, initEvent: AnActionEvent): Boolean {
    return currentTab.openInFindWindow(sessionRef, initEvent)
  }

  fun selectNextTab() {
    currentTabIndex.value = (currentTabIndex.value + 1) % tabVms.size
  }

  fun selectPreviousTab() {
    currentTabIndex.value = (currentTabIndex.value - 1 + tabVms.size) % tabVms.size
  }

  suspend fun canBeShownInFindResults(): Boolean {
    return currentTab.canBeShownInFindResults()
  }

  fun showTab(tabId: String) {
    tabVms.indexOfFirst { it.tabId == tabId }.takeIf { it >= 0 }?.let {
      currentTabIndex.value = it
    }
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

  fun getHistoryItem(next: Boolean) : String {
    val searchText = if (next) historyIterator.next() else historyIterator.prev()
    return searchText
  }

  fun getHistoryItems(): List<String> {
    return historyIterator.getList()
  }

  inner class ShowInFindToolWindowAction(private val onShowFindToolWindow: () -> Unit) : DumbAwareAction(IdeBundle.messagePointer("show.in.find.window.button.name"),
                                                                   IdeBundle.messagePointer("show.in.find.window.button.description")) {
    override fun actionPerformed(e: AnActionEvent) {
      onShowFindToolWindow()
      closePopup()
    }

    override fun update(e: AnActionEvent) {
      if (project == null) {
        e.presentation.isEnabled = false
        return
      }
      e.presentation.isEnabled = canBeShownInFindResults
      e.presentation.icon = getInstance(project).getShowInFindToolWindowIcon()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
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
