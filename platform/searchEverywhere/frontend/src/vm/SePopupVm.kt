// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.ide.IdeBundle
import com.intellij.ide.SearchTopHitProvider.Companion.getTopHitAccelerator
import com.intellij.ide.actions.searcheverywhere.*
import com.intellij.ide.actions.searcheverywhere.SEHeaderActionListener.Companion.SE_HEADER_ACTION_TOPIC
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.PREVIEW_EVENTS
import com.intellij.ide.ui.UISettings
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IncompleteDependenciesService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowManager.Companion.getInstance
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResult
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionsTab
import com.intellij.platform.searchEverywhere.frontend.withPrevious
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.psi.PsiManager
import com.intellij.usageView.UsageInfo
import com.intellij.util.SystemProperties
import com.intellij.util.asDisposable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SePopupVm(
  val coroutineScope: CoroutineScope,
  val session: SeSession,
  private val project: Project?,
  tabs: List<SeTab>,
  deferredTabs: List<SuspendLazyProperty<SeTab?>>,
  adaptedTabs: SuspendLazyProperty<List<SeTab>>,
  initialSearchPattern: String?,
  initialTabId: String,
  private val historyList: SearchHistoryList,
  private val availableLegacyAllTabContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
  private val availableLegacySeparateTabContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
  private val onShowFindToolWindow: (SePopupVm) -> Unit,
  private val closePopupHandler: () -> Unit,
) {
  private val _searchPattern: MutableStateFlow<String> = MutableStateFlow("")
  val searchPattern: StateFlow<String> = _searchPattern.asStateFlow()

  private val _deferredTabVms = MutableSharedFlow<SeTabVm>(replay = 100)
  private val _tabsModelFlow = MutableStateFlow(run {
    val tabsVms = tabs.map {
      SeTabVm(project, coroutineScope, it, searchPattern, availableLegacyAllTabContributors)
    }
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

    _searchPattern.value = initialSearchPattern ?: run {
      // History could be suppressed by the user for some reason (creating promo video, conference demo etc.)
      // or could be suppressed just for All tab in the registry.
      val suppressHistory = SystemProperties.getBooleanProperty("idea.searchEverywhere.noHistory", false) ||
                            SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID == currentTab.tabId &&
                            Registry.`is`("search.everywhere.disable.history.for.all")
      if (!suppressHistory) historyIterator.next() else ""
    }

    coroutineScope.launch {
      _deferredTabVms.collect { tabVm ->
        _tabsModelFlow.update { model ->
          model.newModelWith(tabVm)
        }
      }
    }

    coroutineScope.launch {
      currentTabFlow.collect { tabVm ->
        canBeShownInFindResultsFlow.update { tabVm.canBeShownInFindResults() }
      }
    }

    val deferredTabJobs = deferredTabs.map {
      coroutineScope.launch {
        it.getValue()?.let { tab ->
          _deferredTabVms.emit(SeTabVm(project, coroutineScope, tab, searchPattern, availableLegacyAllTabContributors))
        }
      }
    }

    coroutineScope.launch {
      deferredTabJobs.joinAll()

      val adaptedTabs = adaptedTabs.getValue()
      adaptedTabs.forEach { tab ->
        val providerId = tab.id.toProviderId()
        val availableLegacyContributor = availableLegacySeparateTabContributors[providerId] ?: return@forEach
        _deferredTabVms.emit(SeTabVm(project, coroutineScope, tab, searchPattern, mapOf(providerId to availableLegacyContributor)))
      }
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
      previewConfigurationFlow = combine(currentTabFlow, showPreviewSetting) { tabVm, previewSetting ->
        tabVm.isPreviewEnabled.getValue() to previewSetting
      }.mapLatest { (tabPreviewEnabled, previewSetting) ->
        if (tabPreviewEnabled) {
          if (previewSetting) SePreviewConfiguration(previewFetcher.project, this::fetchPreview)
          else SePreviewConfiguration(previewFetcher.project, null)
        }
        else {
          SePreviewConfiguration(previewFetcher.project, null)
        }
      }
    }
    else {
      previewConfigurationFlow = flowOf(null)
    }
  }

  suspend fun itemsSelected(indexedItems: List<Pair<Int, SeItemData>>, areIndexesOriginal: Boolean, modifiers: Int): List<SeSelectionResult> {
    val currentTab = currentTab

    return coroutineScope {
      indexedItems.map { item ->
        async {
          currentTab.itemSelected(item, areIndexesOriginal, modifiers, searchPattern.value)
        }
      }.awaitAll()
    }
  }

  suspend fun openInFindWindow(session: SeSession, initEvent: AnActionEvent): Boolean {
    return currentTab.openInFindWindow(session, initEvent)
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
    _searchPattern.value = text
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
}

@ApiStatus.Internal
class SePreviewConfiguration(
  val project: Project,
  val fetchPreview: (suspend (SeItemData) -> (List<UsageInfo>?))?,
)

@ApiStatus.Internal
class SeTabsModel(tabVms: List<SeTabVm>, selectedTabId: String) {
  val sortedTabVms: List<SeTabVm> = tabVms.sortedBy { -it.priority }

  val selectedTabIndexFlow: MutableStateFlow<Int> = MutableStateFlow(sortedTabVms.indexOfFirst { it.tabId == selectedTabId })
  val selectedTabFlow: Flow<SeTabVm> = selectedTabIndexFlow.map { sortedTabVms[it] }
  val selectedTab: SeTabVm get() = sortedTabVms[selectedTabIndexFlow.value]

  init {
    require(sortedTabVms.isNotEmpty()) { "Search Everywhere tabs must not be empty" }
    require(selectedTabIndexFlow.value != -1) { "Selected tab ID must be present in the list of tabs" }
  }

  fun selectNextTab() {
    selectedTabIndexFlow.update { (it + 1) % sortedTabVms.size }
  }

  fun selectPreviousTab() {
    selectedTabIndexFlow.update { (it - 1 + sortedTabVms.size) % sortedTabVms.size }
  }

  fun showTab(tabId: String) {
    sortedTabVms.indexOfFirst { it.tabId == tabId }.takeIf { it != -1 }?.let { selectedTabIndexFlow.value = it }
  }

  fun newModelWith(tab: SeTabVm): SeTabsModel = SeTabsModel(sortedTabVms + tab, selectedTab.tabId)
}