// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.ide.IdeBundle
import com.intellij.ide.SearchTopHitProvider.Companion.getTopHitAccelerator
import com.intellij.ide.actions.searcheverywhere.HistoryIterator
import com.intellij.ide.actions.searcheverywhere.PREVIEW_ACTION_ID
import com.intellij.ide.actions.searcheverywhere.PreviewExperiment
import com.intellij.ide.actions.searcheverywhere.SEHeaderActionListener
import com.intellij.ide.actions.searcheverywhere.SEHeaderActionListener.Companion.SE_HEADER_ACTION_TOPIC
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManagerImpl
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewFetcher
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.PREVIEW_EVENTS
import com.intellij.ide.actions.searcheverywhere.SearchHistoryList
import com.intellij.ide.ui.UISettings
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IncompleteDependenciesService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResult
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabInfo
import com.intellij.platform.searchEverywhere.frontend.SeTabsCustomizer
import com.intellij.platform.searchEverywhere.frontend.ml.SeMlService
import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionsTab
import com.intellij.platform.searchEverywhere.frontend.withPrevious
import com.intellij.platform.searchEverywhere.providers.SeLegacyContributors
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.util.SystemProperties
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SePopupVm(
  val coroutineScope: CoroutineScope,
  val session: SeSession,
  private val project: Project?,
  tabs: List<SeTab>,
  initialDummyTabVms: List<SeDummyTabVm>,
  deferredTabs: List<SuspendLazyProperty<SeTab?>>,
  adaptedTabs: SuspendLazyProperty<List<SeTab>>,
  initialSearchPattern: String?,
  initialTabId: String,
  private val historyList: SearchHistoryList,
  private val availableLegacyContributors: SeLegacyContributors,
  private val onShowFindToolWindow: (SePopupVm) -> Unit,
  private val closePopupHandler: () -> Unit,
) {
  private val _searchPattern: MutableStateFlow<String> = MutableStateFlow("")
  val searchPattern: StateFlow<String> = _searchPattern.asStateFlow()

  private val _deferredTabVms = MutableSharedFlow<SeTabInitEvent>(replay = 100)
  private val _tabsModelFlow = MutableStateFlow(run {
    val customizer = SeTabsCustomizer.getInstance()
    val initializedTabVms = tabs.mapNotNull {
      val tabInfo = customizer.customizeTabInfo(it.id, SeTabInfo(it.priority, it.name)) ?: return@mapNotNull null
      SeTabVmImpl(project, coroutineScope, it, tabInfo, searchPattern, availableLegacyContributors.allTab)
    }

    val initialisedTabVmIds = initializedTabVms.map { it.tabId }.toSet()
    val tabsVms = initializedTabVms + initialDummyTabVms.filter { !initialisedTabVmIds.contains(it.tabId) }

    // Just for safety in case the initially selected tab is not in the initial tabs
    val initialTabId =
      if (tabsVms.containsId(initialTabId)) initialTabId
      else tabsVms.first().tabId

    SeTabsModel(tabsVms, initialTabId)
  })
  val tabsModelFlow: StateFlow<SeTabsModel> get() = _tabsModelFlow.asStateFlow()
  val tabsModel: SeTabsModel get() = tabsModelFlow.value
  val currentTabFlow: Flow<SeTabVm>
  val currentTab: SeTabVm get() = tabsModelFlow.value.selectedTab

  private val canBeShownInFindResultsFlow = MutableStateFlow(false)
  val canBeShownInFindResults: Boolean get() = canBeShownInFindResultsFlow.value

  data class SearchFieldHint(val text: String?, val tooltip: String?, val isWarning: Boolean)

  private val _searchFieldHint = MutableStateFlow<SearchFieldHint?>(null)
  val searchFieldHint: StateFlow<SearchFieldHint?> = _searchFieldHint

  private var historyIterator: HistoryIterator = historyList.getIterator(currentTab.tabId)
    get() {
      val selectedContributorID = currentTab.tabId
      if (field.getContributorID() != selectedContributorID) {
        field = historyList.getIterator(selectedContributorID)
      }
      return field
    }

  val previewConfigurationFlow: Flow<SePreviewConfiguration?>
  private val showPreviewSetting = MutableStateFlow(UISettings.getInstance().showPreviewInSearchEverywhere)

  private val previewFetcher =
    if (project == null) null
    else SearchEverywherePreviewFetcher(project = project, publishPreviewTime = { selectedItem, duration ->
      previewTopicPublisher.onPreviewDataReady(project, selectedItem, duration)
    }, coroutineScope.asDisposable())
  private val previewTopicPublisher = ApplicationManager.getApplication().getMessageBus().syncPublisher(PREVIEW_EVENTS)

  init {
    check(tabs.isNotEmpty()) { "Search Everywhere tabs must not be empty" }

    currentTabFlow = tabsModelFlow.flatMapLatest { it.selectedTabFlow }.withPrevious().map { (prev, next) ->
      prev?.setActive(false)
      next.setActive(true)
      next
    }

    coroutineScope.launch(Dispatchers.UI) {
      _searchPattern.collect { pattern -> SeLog.log(SeLog.PATTERN) { "SePopupVm: received pattern ['$pattern']" } }
    }

    _searchPattern.value = initialSearchPattern ?: run {
      // History could be suppressed by the user for some reason (creating promo video, conference demo etc.)
      // or could be suppressed just for All tab in the registry.
      val suppressHistory = SystemProperties.getBooleanProperty("idea.searchEverywhere.noHistory", false) ||
                            SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == currentTab.tabId &&
                            Registry.`is`("search.everywhere.disable.history.for.all")
      if (!suppressHistory) historyIterator.next() else ""
    }

    coroutineScope.launch {
      _deferredTabVms.collect { tabInitEvent ->
        _tabsModelFlow.update { model ->
          model.newModelWithReplacedTab(tabInitEvent.newTabs, removeDummy = tabInitEvent.removeDummy)
        }
      }
    }

    coroutineScope.launch {
      currentTabFlow.collect { tabVm ->
        canBeShownInFindResultsFlow.update { tabVm.canBeShownInFindResults() }
      }
    }

    val tabsCustomizer = SeTabsCustomizer.getInstance()
    val deferredTabJobs = deferredTabs.map {
      coroutineScope.launch {
        it.getValue()?.let { tab ->
          val newInfo = tabsCustomizer.customizeTabInfo(tab.id, SeTabInfo(tab.priority, tab.name)) ?: return@launch
          val tabVm = SeTabVmImpl(project, coroutineScope, tab, newInfo, searchPattern, availableLegacyContributors.allTab)
          _deferredTabVms.emit(SeTabInitEvent(listOf(tabVm)))
        }
      }
    }

    coroutineScope.launch {
      deferredTabJobs.joinAll()

      val adaptedTabs = adaptedTabs.getValue()
      val adaptedCustomized = adaptedTabs.mapNotNull { tab ->
        val providerId = tab.id.toProviderId()
        val availableLegacyContributor = availableLegacyContributors.separateTab[providerId] ?: return@mapNotNull null

        tabsCustomizer.customizeTabInfo(tab.id, SeTabInfo(tab.priority, tab.name))?.let { tabInfo ->
          SeTabVmImpl(project, coroutineScope, tab, tabInfo, searchPattern, mapOf(providerId to availableLegacyContributor))
        }
      }

      SeLog.log(SeLog.LIFE_CYCLE) { "Initialized deferred adapted tabs: [${adaptedCustomized.joinToString { it.tabId }}]" }
      _deferredTabVms.emit(SeTabInitEvent(adaptedCustomized, true))
    }

    coroutineScope.launch {
      project ?: return@launch
      combine(
        currentTabFlow,
        DumbService.getInstance(project).state,
        project.service<IncompleteDependenciesService>().stateFlow
      ) { currentTab, dumbMode, dependenciesState ->
        Triple(currentTab, dumbMode.isDumb, !dependenciesState.isComplete)
      }.map { (currentTab, isDumb, isIncomplete) ->
        // IJPL-193615: In RemDev, IncompleteDependenciesService state is not synchronized between frontend and backend,
        // so isIncomplete always remains false on frontend, making dependency loading messages unavailable in RemDev.
        if (currentTab.isIndexingDependent && isDumb) {
          if (currentTab.tabId == SeActionsTab.ID) {
            SearchFieldHint(IdeBundle.message("dumb.mode.analyzing.project"), IdeBundle.message("dumb.mode.some.actions.might.be.unavailable.during.project.analysis"), true)
          }
          else {
            SearchFieldHint(IdeBundle.message("dumb.mode.analyzing.project"), IdeBundle.message("dumb.mode.results.might.be.incomplete.during.project.analysis"), true)
          }
        }
        else if (currentTab.isIndexingDependent && isIncomplete) {
          SearchFieldHint(IdeBundle.message("incomplete.mode.results.might.be.incomplete"), null, true)
        }
        else {
          if (currentTab.isCommandsSupported()) {
            SearchFieldHint(IdeBundle.message("searcheverywhere.textfield.hint", getTopHitAccelerator()), null, false)
          }
          else null
        }
      }.distinctUntilChanged().collect {
        _searchFieldHint.value = it
      }
    }

    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(
      SE_HEADER_ACTION_TOPIC,
      object : SEHeaderActionListener {
        override fun performed(event: SEHeaderActionListener.SearchEverywhereActionEvent) {
          if (event.actionID == PREVIEW_ACTION_ID) {
            showPreviewSetting.value = UISettings.getInstance().showPreviewInSearchEverywhere
          }
        }
      }
    )

    if (PreviewExperiment.isExperimentEnabled && previewFetcher != null) {
      previewConfigurationFlow = currentTabFlow.flatMapLatest { tabVm ->
        val tabPreviewEnabled = tabVm.isPreviewEnabled.getValue()
        showPreviewSetting.map { previewSetting ->
          if (tabPreviewEnabled && previewSetting) SePreviewConfiguration(previewFetcher.project, this::fetchPreview)
          else SePreviewConfiguration(previewFetcher.project, null)
        }
      }
    }
    else {
      previewConfigurationFlow = flowOf(null)
    }
  }

  suspend fun itemsSelected(indexedItems: List<Pair<Int, SeItemData>>, areIndexesOriginal: Boolean, modifiers: Int): List<SeSelectionResult> {
    val currentTab = currentTab

    SeMlService.getInstanceIfEnabled()?.onResultsSelected(indexedItems)

    return coroutineScope {
      indexedItems.map { item ->
        async { currentTab.itemSelected(item, areIndexesOriginal, modifiers, searchPattern.value) }
      }.awaitAll()
    }
  }

  suspend fun openInFindWindow(session: SeSession): Boolean {
    return currentTab.openInFindWindow(session)
  }

  fun selectNextTab() {
    tabsModel.selectNextTab()
  }

  fun selectPreviousTab() {
    tabsModel.selectPreviousTab()
  }

  suspend fun canBeShownInFindResults(): Boolean {
    return currentTab.canBeShownInFindResults()
  }

  fun showTab(tabId: String) {
    tabsModel.showTab(tabId)
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

  fun getHistoryItem(next: Boolean): String {
    val searchText = if (next) historyIterator.next() else historyIterator.prev()
    return searchText
  }

  fun getHistoryItems(): List<String> {
    return historyIterator.getList()
  }

  fun setSearchText(text: String) {
    SeLog.log(SeLog.PATTERN) { "SePopupVm: setting text: ['$text']" }
    _searchPattern.value = text
    coroutineScope.launch(Dispatchers.UI) {
      currentTab.filterEditor.getValue()?.closeFilterPopup()
    }
  }

  private suspend fun fetchPreview(newValue: SeItemData): List<UsageInfo>? {
    if (project == null) return null
    val usageInfo = currentTab.getPreviewInfo(newValue) ?: return null

    val virtualFile = usageInfo.fileUrl.virtualFile() ?: return null

    val usages = readAction {
      val psiElement = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction null

      usageInfo.navigationRanges.map { (start, end) ->
        UsageInfo(psiElement, start, end, false)
      }
    } ?: return null

    return previewFetcher?.fetchPreview(usages)
  }

  suspend fun isExtendedInfoEnabled(): Boolean {
    return SearchEverywhereUI.isExtendedInfoEnabled() && currentTab.isExtendedInfoEnabled()
  }

  private val popupVm = this

  inner class ShowInFindToolWindowAction : DumbAwareAction(IdeBundle.messagePointer("show.in.find.window.button.name"),
                                                           IdeBundle.messagePointer("show.in.find.window.button.description")) {
    override fun actionPerformed(e: AnActionEvent) {
      onShowFindToolWindow(popupVm)
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

  private class SeTabInitEvent(val newTabs: List<SeTabVm>, val removeDummy: Boolean = false)
}

@ApiStatus.Internal
class SePreviewConfiguration(
  val project: Project,
  val fetchPreview: (suspend (SeItemData) -> (List<UsageInfo>?))?,
)

@ApiStatus.Internal
class SeTabsModel(tabVms: List<SeTabVm>, selectedTabId: String) {
  val sortedTabVms: List<SeTabVm> = tabVms.sortedBy { -it.priority }
  private val size get() = sortedTabVms.size

  val selectedTabIdFlow: MutableStateFlow<String> = MutableStateFlow(
    if (sortedTabVms.any { it.tabId == selectedTabId }) selectedTabId
    else sortedTabVms.first().tabId
  )
  val selectedTabFlow: Flow<SeTabVm> = selectedTabIdFlow.mapNotNull { id -> sortedTabVms.firstOrNull { it.tabId == id } }
  val selectedTab: SeTabVm get() = sortedTabVms.firstOrNull { it.tabId == selectedTabIdFlow.value } ?: sortedTabVms.first()

  init {
    require(sortedTabVms.isNotEmpty()) { "Search Everywhere tabs must not be empty" }
    SeLog.log(SeLog.LIFE_CYCLE) { "SeTabsModel built: tabs=[${sortedTabVms.joinToString { it.tabId }}], selected=${selectedTabIdFlow.value}" }
  }

  private fun indexOf(tabId: String): Int = sortedTabVms.indexOfFirst { it.tabId == tabId }.coerceAtLeast(0)

  fun selectNextTab() {
    selectedTabIdFlow.update { sortedTabVms[(indexOf(it) + 1) % size].tabId }
  }

  fun selectPreviousTab() {
    selectedTabIdFlow.update { sortedTabVms[(indexOf(it) - 1 + size) % size].tabId }
  }

  fun showTab(tabId: String) {
    if (contains(tabId)) selectedTabIdFlow.value = tabId
  }

  fun contains(tabId: String): Boolean = sortedTabVms.any { it.tabId == tabId }

  fun newModelWithReplacedTab(newTabs: List<SeTabVm>, selectedTabId: String? = null, removeDummy: Boolean = false): SeTabsModel {
    val newTabs = newTabs.associateBy { it.tabId }.toMutableMap()

    val mergedTabs = (sortedTabVms.map {
      newTabs.remove(it.tabId) ?: return@map it
    } + newTabs.values).let {
      if (removeDummy) it.filterIsInstance<SeTabVmImpl>()
      else it
    }

    return SeTabsModel(mergedTabs, selectedTabId ?: selectedTab.tabId)
  }
}

private fun List<SeTabVm>.containsId(tabId: String): Boolean = any { it.tabId == tabId }
