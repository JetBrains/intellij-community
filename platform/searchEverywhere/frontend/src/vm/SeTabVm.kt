// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.ide.rpc.ThrottledItems
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.ide.rpc.throttledWithAccumulation
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataKeys
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeResultAddedEvent
import com.intellij.platform.searchEverywhere.SeResultEndEvent
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeResultReplacedEvent
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.AutoToggleAction
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResult
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResultClose
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResultKeep
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResultText
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabInfo
import com.intellij.platform.searchEverywhere.frontend.ml.SeMlService
import com.intellij.platform.searchEverywhere.frontend.ui.SePopupHeaderPane
import com.intellij.platform.searchEverywhere.isCommand
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.providers.SeAdaptedItem
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import com.intellij.platform.searchEverywhere.withPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.UUID
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@ApiStatus.Internal
sealed interface SeTabVm {
  val tabId: String
  val name: String
  val priority: Int
  val reportableTabId: String

  val searchResults: StateFlow<SeSearchContext?>
  val isIndexingDependent: Boolean
  val isPreviewEnabled: SuspendLazyProperty<Boolean>
  var lastNotFoundString: String?
  val filterEditor: SuspendLazyProperty<SeFilterEditor?>
  var shouldLoadMore: Boolean
  val resultsHitBackPressureFlow: Flow<Pair<String, Boolean>>

  fun setActive(isActive: Boolean)
  suspend fun isExtendedInfoEnabled(): Boolean
  suspend fun isCommandsSupported(): Boolean
  suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo?
  suspend fun itemSelected(itemWithIndex: Pair<Int, SeItemData>, isIndexOriginal: Boolean, modifiers: Int, searchText: String): SeSelectionResult
  suspend fun openInFindWindow(session: SeSession, initEvent: AnActionEvent): Boolean
  suspend fun canBeShownInFindResults(): Boolean
  suspend fun getSearchEverywhereToggleAction(): SearchEverywhereToggleAction?
  suspend fun getUpdatedPresentation(item: SeItemData): SeItemPresentation?
  suspend fun performExtendedAction(item: SeItemData): Boolean
  suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo?
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
@ApiStatus.Internal
class SeTabVmImpl(
  private val project: Project?,
  coroutineScope: CoroutineScope,
  private val tab: SeTab,
  private val customizedTabInfo: SeTabInfo,
  private val searchPattern: StateFlow<String>,
  private val availableLegacyContributors: Map<SeProviderId, SearchEverywhereContributor<Any>>,
) : SeTabVm {
  override val tabId: String get() = tab.id
  override val name: String get() = customizedTabInfo.name
  override val priority: Int get() = customizedTabInfo.priority

  override val searchResults: StateFlow<SeSearchContext?> get() = _searchResults.asStateFlow()
  override val filterEditor: SuspendLazyProperty<SeFilterEditor?> = initAsync(coroutineScope) { tab.getFilterEditor() }
  override val reportableTabId: String =
    if (SearchEverywhereUsageTriggerCollector.isReportable(tab)) tabId
    else SearchEverywhereUsageTriggerCollector.NOT_REPORTABLE_ID

  override val isIndexingDependent: Boolean get() = tab.isIndexingDependent
  override val isPreviewEnabled: SuspendLazyProperty<Boolean> = initAsync(coroutineScope) { tab.isPreviewEnabled() }

  private val shouldLoadMoreFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
  override var shouldLoadMore: Boolean
    get() = shouldLoadMoreFlow.value
    set(value) {
      shouldLoadMoreFlow.value = value
    }

  private val _resultsHitBackPressureFlow: MutableSharedFlow<Pair<String, Boolean>> = MutableSharedFlow()
  override val resultsHitBackPressureFlow: Flow<Pair<String, Boolean>> = _resultsHitBackPressureFlow.asSharedFlow().mapLatest {
    if (it.second) delay(300)
    it
  }.filter { it.second }

  private val _searchResults: MutableStateFlow<SeSearchContext?> = MutableStateFlow(null)
  private val isActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

  private val dumbModeStateFlow =
    if (project == null) flowOf(false)
    else {
      MutableStateFlow(DumbService.isDumb(project)).also {
        project.messageBus.connect(coroutineScope).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
          override fun enteredDumbMode() {
            it.value = true
          }

          override fun exitDumbMode() {
            it.value = false
          }
        })
      }
    }

  override var lastNotFoundString: String? = null

  init {
    coroutineScope.launch {
      isActiveFlow.combine(dumbModeStateFlow) { isActive, _ ->
        isActive
      }.collectLatest { isActive ->
        if (!isActive) {
          _searchResults.value = null
          return@collectLatest
        }

        val searchPatternWithAutoToggle = searchPattern.onEach {
          withContext(Dispatchers.EDT) {
            if (lastNotFoundString != null) {
              val newPatternContainsPrevious = lastNotFoundString!!.length > 1 && it.contains(lastNotFoundString!!)
              if (!newPatternContainsPrevious) {
                SeLog.log(SeLog.PATTERN) { "SeTabVm<$tabId>: resetting auto toggle due to pattern family change" }
                (getSearchEverywhereToggleAction() as? AutoToggleAction)?.autoToggle(false)
              }
            }
          }
        }

        val shouldThrottle = AtomicBoolean(false)

        combine(searchPatternWithAutoToggle, filterEditor.getValue()?.resultFlow ?: flowOf(null)) { searchPattern, filterData ->
          Pair(searchPattern, filterData ?: SeFilterState.Empty)
        }.mapLatest { (searchPattern, filterData) ->
          val params = SeParams(searchPattern, filterData)
          val searchId = UUID.randomUUID().toString()

          SeMlService.getInstanceIfEnabled()?.onStateStarted(this@SeTabVmImpl.tabId, params)

          val resultsFlow = tab.getItems(params).let { resultsFlow ->
            val resultsFlowWithAdaptedPresentations = resultsFlow.mapNotNull {
              checkAndAddMissingPresentationIfPossible(it)
                ?.let { withPresentation ->
                  calculateMlWeight(withPresentation)
                }
            }

            val essential = tab.essentialProviderIds()
            if (essential.isEmpty()) {
              if (shouldThrottle.load()) resultsFlowWithAdaptedPresentations.throttledWithAccumulation(shouldPassItem = { item -> item !is SeResultEndEvent })
              else resultsFlowWithAdaptedPresentations.map { event -> ThrottledOneItem(event) }
            }
            else resultsFlowWithAdaptedPresentations.throttleUntilEssentialsArrive(essential)
          }.map { item ->
            if (!shouldLoadMoreFlow.value) _resultsHitBackPressureFlow.emit(searchId to true)
            shouldLoadMoreFlow.first { it }
            _resultsHitBackPressureFlow.emit(searchId to false)
            item
          }

          shouldThrottle.store(true)
          SeSearchContext(searchId, tabId, searchPattern, resultsFlow)
        }.collect {
          if (!isActiveFlow.value) return@collect
          _searchResults.value = it
        }
      }
    }.invokeOnCompletion {
      Disposer.dispose(tab)
    }
  }

  override fun setActive(isActive: Boolean) {
    if (!isActiveFlow.value && isActive) shouldLoadMore = true

    isActiveFlow.value = isActive
  }

  override suspend fun itemSelected(itemWithIndex: Pair<Int, SeItemData>, isIndexOriginal: Boolean, modifiers: Int, searchText: String): SeSelectionResult {
    logItemSelectedEvent(itemWithIndex, isIndexOriginal)

    if (itemWithIndex.second.isCommand) {
      return SeSelectionResultText(itemWithIndex.second.presentation.text)
    }

    return if (tab.itemSelected(itemWithIndex.second, modifiers, searchText))
      SeSelectionResultClose()
    else
      SeSelectionResultKeep()
  }

  private fun logItemSelectedEvent(itemWithIndex: Pair<Int, SeItemData>, isIndexOriginal: Boolean) {
    val (index, itemData) = itemWithIndex
    val data = mutableListOf<EventPair<*>>()

    data.add(SearchEverywhereUsageTriggerCollector.CURRENT_TAB_FIELD.with(reportableTabId))
    data.add(SearchEverywhereUsageTriggerCollector.SELECTED_ITEM_NUMBER.with(index))
    data.add(SearchEverywhereUsageTriggerCollector.HAS_ONLY_SIMILAR_ELEMENT.with(isIndexOriginal))
    data.add(SearchEverywhereUsageTriggerCollector.IS_SPLIT.with(true))

    itemData.additionalInfo[SeItemDataKeys.REPORTABLE_PROVIDER_ID]?.let { reportableContributorID ->
      data.add(SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(reportableContributorID))
    }

    itemData.additionalInfo[SeItemDataKeys.IS_SEMANTIC]?.let { isSemantic ->
      data.add(SearchEverywhereUsageTriggerCollector.IS_ELEMENT_SEMANTIC.with(isSemantic.toBoolean()))
    }

    itemData.additionalInfo[SeItemDataKeys.PSI_LANGUAGE_ID]?.let { languageId ->
      Language.findLanguageByID(languageId)?.let { language ->
        data.add(EventFields.Language.with(language))
      }
    }

    SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ITEM_SELECTED.log(project, data)
  }

  private fun checkAndAddMissingPresentationIfPossible(resultEvent: SeResultEvent): SeResultEvent? {
    val itemData = resultEvent.itemDataOrNull() ?: return resultEvent

    return if (itemData.presentation is SeAdaptedItemEmptyPresentation) {
      availableLegacyContributors[itemData.providerId]?.let { contributor ->
        val fetchedItem = itemData.fetchItemIfExists() as? SeAdaptedItem ?: return null
        val newItemData = itemData.withPresentation(SeAdaptedItemPresentation(itemData.presentation.isMultiSelectionSupported,
                                                                              fetchedItem.rawObject) {
          contributor.elementsRenderer
        })

        when (resultEvent) {
          is SeResultEndEvent -> resultEvent
          is SeResultAddedEvent -> SeResultAddedEvent(newItemData)
          is SeResultReplacedEvent -> SeResultReplacedEvent(resultEvent.uuidsToReplace, newItemData)
        }
      }
    }
    else {
      resultEvent
    }
  }

  private fun calculateMlWeight(resultEvent: SeResultEvent): SeResultEvent {
    if (resultEvent !is SeResultAddedEvent && resultEvent !is SeResultReplacedEvent) return resultEvent
    val mlService = SeMlService.getInstanceIfEnabled() ?: return resultEvent

    val itemData = resultEvent.itemDataOrNull() ?: return resultEvent
    val newItemData = mlService.applyMlWeight(itemData)

    return when (resultEvent) {
      is SeResultAddedEvent -> SeResultAddedEvent(newItemData)
      is SeResultReplacedEvent -> SeResultReplacedEvent(resultEvent.uuidsToReplace, newItemData)
    }
  }

  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo? {
    return tab.getEmptyResultInfo(context)
  }

  override suspend fun canBeShownInFindResults(): Boolean {
    return tab.canBeShownInFindResults()
  }

  override suspend fun openInFindWindow(session: SeSession, initEvent: AnActionEvent): Boolean {
    val params = SeParams(searchPattern.value,
                          filterEditor.getValue()?.resultFlow?.value ?: SeFilterState.Empty)
    return tab.openInFindToolWindow(session, params, initEvent)
  }

  override suspend fun getSearchEverywhereToggleAction(): SearchEverywhereToggleAction? {
    return tab.getFilterEditor()?.getHeaderActions()?.firstOrNull {
      it is SearchEverywhereToggleAction
    } as? SearchEverywhereToggleAction
  }

  override suspend fun getUpdatedPresentation(item: SeItemData): SeItemPresentation? {
    if (item.presentation is SeAdaptedItemPresentation) return null
    return tab.getUpdatedPresentation(item)
  }

  /**
   * @return true if the popup should be closed, false otherwise
   */
  override suspend fun performExtendedAction(item: SeItemData): Boolean {
    return tab.performExtendedAction(item)
  }

  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? = tab.getPreviewInfo(itemData)

  override suspend fun isExtendedInfoEnabled() : Boolean {
    return tab.isExtendedInfoEnabled()
  }

  override suspend fun isCommandsSupported(): Boolean {
    return tab.isCommandsSupported()
  }
}

private const val ESSENTIALS_THROTTLE_DELAY: Long = 100
private const val ESSENTIALS_ENOUGH_COUNT: Int = 15
private const val FAST_PASS_THROTTLE: Long = 100

private fun Flow<SeResultEvent>.throttleUntilEssentialsArrive(essentialProviderIds: Set<SeProviderId>): Flow<ThrottledItems<SeResultEvent>> {
  val essentialProvidersCounts = essentialProviderIds.associateWith { 0 }.toMutableMap()
  val essentialWaitingTimeout: Long = AdvancedSettings.getInt("search.everywhere.contributors.wait.timeout").toLong()

  SeLog.log(SeLog.THROTTLING) { "Will start throttle with essential providers: $essentialProviderIds" }

  return throttledWithAccumulation(
    resultThrottlingMs = essentialWaitingTimeout,
    shouldPassItem = { it !is SeResultEndEvent },
    fastPassThrottlingMs = FAST_PASS_THROTTLE,
    shouldFastPassItem = { it.itemDataOrNull()?.shouldIgnoreThrottling() == true }
  ) { event: SeResultEvent, _: Int ->
    val providerId = event.providerId()

    when (event) {
      is SeResultEndEvent -> {
        if (essentialProvidersCounts.remove(providerId) != null) {
          SeLog.log(SeLog.THROTTLING) { "Ended: $providerId" }
        }
      }
      else -> {
        essentialProvidersCounts[providerId]?.let {
          SeLog.log(SeLog.THROTTLING) { "Arrived: $providerId ($it)" }
          essentialProvidersCounts[providerId] = it + 1
        }
      }
    }

    return@throttledWithAccumulation if (essentialProvidersCounts.isEmpty()) ESSENTIALS_THROTTLE_DELAY
    else if (essentialProvidersCounts.values.all { it >= ESSENTIALS_ENOUGH_COUNT }) 0
    else if (essentialProvidersCounts.values.all { it > 0 }) ESSENTIALS_THROTTLE_DELAY
    else null
  }
}

private fun SeResultEvent.providerId() = when (this) {
  is SeResultAddedEvent -> itemData.providerId
  is SeResultReplacedEvent -> newItemData.providerId
  is SeResultEndEvent -> providerId
}

private fun SeResultEvent.itemDataOrNull(): SeItemData? = when (this) {
  is SeResultAddedEvent -> itemData
  is SeResultReplacedEvent -> newItemData
  is SeResultEndEvent -> null
}

private fun SeItemData.shouldIgnoreThrottling(): Boolean =
  AdvancedSettings.getBoolean("search.everywhere.recent.at.top") &&
  this.providerId.value == SeProviderIdUtils.RECENT_FILES_ID ||
  this.isCommand

@ApiStatus.Internal
class SeSearchContext(val searchId: String, val tabId: String,
                      val searchPattern: String,
                      val resultsFlow: Flow<ThrottledItems<SeResultEvent>>)

@ApiStatus.Internal
class SeDummyTabVm private constructor(
  override val tabId: String,
  override val name: @Nls String,
  override val priority: Int,
  override val reportableTabId: String,
) : SeTabVm {

  constructor(tab: SePopupHeaderPane.Tab) : this(tab.id, tab.name, tab.priority, tab.reportableId)
  constructor(id: String, tab: SeTabInfo) : this(id, tab.name, tab.priority, id)

  override val searchResults: StateFlow<SeSearchContext?> get() = MutableStateFlow(null)
  override val isIndexingDependent: Boolean get() = true
  override val isPreviewEnabled: SuspendLazyProperty<Boolean> get() = SuspendLazyProperty { false }
  override var lastNotFoundString: String? = null
  override val filterEditor: SuspendLazyProperty<SeFilterEditor?> = SuspendLazyProperty { null }
  override var shouldLoadMore: Boolean = false
  override val resultsHitBackPressureFlow: Flow<Pair<String, Boolean>> = emptyFlow()

  override fun setActive(isActive: Boolean) {}
  override suspend fun isExtendedInfoEnabled(): Boolean = false
  override suspend fun isCommandsSupported(): Boolean = false
  override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? = null
  override suspend fun itemSelected(itemWithIndex: Pair<Int, SeItemData>, isIndexOriginal: Boolean, modifiers: Int, searchText: String): SeSelectionResult = SeSelectionResultKeep()
  override suspend fun openInFindWindow(session: SeSession, initEvent: AnActionEvent): Boolean = false
  override suspend fun canBeShownInFindResults(): Boolean = false
  override suspend fun getSearchEverywhereToggleAction(): SearchEverywhereToggleAction? = null
  override suspend fun getUpdatedPresentation(item: SeItemData): SeItemPresentation? = null
  override suspend fun performExtendedAction(item: SeItemData): Boolean = false
  override suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo? = null
}
