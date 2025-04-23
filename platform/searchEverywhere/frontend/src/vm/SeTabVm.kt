// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeEmptyResultInfo
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.*
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.initAsync
import fleet.kernel.DurableRef
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
@ApiStatus.Internal
class SeTabVm(
  private val project: Project?,
  coroutineScope: CoroutineScope,
  private val tab: SeTab,
  private val searchPattern: StateFlow<String>,
) {
  val searchResults: StateFlow<Flow<SeThrottledItems<SeResultEvent>>> get() = _searchResults.asStateFlow()
  val name: String get() = tab.name
  val filterEditor: SuspendLazyProperty<SeFilterEditor?> = initAsync(coroutineScope) { tab.getFilterEditor() }
  val tabId: String get() = tab.id
  val reportableTabId: String =
    if (SearchEverywhereUsageTriggerCollector.isReportable(tab)) tabId
    else SearchEverywhereUsageTriggerCollector.NOT_REPORTABLE_ID

  private val shouldLoadMoreFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
  var shouldLoadMore: Boolean
    get() = shouldLoadMoreFlow.value
    set(value) {
      shouldLoadMoreFlow.value = value
    }

  private val _searchResults: MutableStateFlow<Flow<SeThrottledItems<SeResultEvent>>> = MutableStateFlow(emptyFlow())
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

  init {
    coroutineScope.launch {
      isActiveFlow.combine(dumbModeStateFlow) { isActive, _ ->
        isActive
      }.collectLatest { isActive ->
        if (!isActive) return@collectLatest

        val searchPatternWithAutoToggle = searchPattern.withNewValueSideEffect { old, new ->
          if (!new.startsWith(old)) {
            withContext(Dispatchers.EDT) {
              (getSearchEverywhereToggleAction() as? AutoToggleAction)?.autoToggle(false)
            }
          }
        }

        val shouldThrottle = AtomicBoolean(false)

        combine(searchPatternWithAutoToggle, filterEditor.getValue()?.resultFlow ?: flowOf(null)) { searchPattern, filterData ->
          Pair(searchPattern, filterData ?: SeFilterState.Empty)
        }.mapLatest { (searchPattern, filterData) ->
          val params = SeParams(searchPattern, filterData)

          val resultsFlow = tab.getItems(params).let {
            if (shouldThrottle.load()) it.throttledWithAccumulation()
            else it.map { event -> SeThrottledOneItem(event) }
          }.map { item ->
            shouldLoadMoreFlow.first { it }
            item
          }

          shouldThrottle.store(true)
          resultsFlow
        }.collect {
          _searchResults.value = it
        }
      }
    }.invokeOnCompletion {
      Disposer.dispose(tab)
    }
  }

  fun setActive(isActive: Boolean) {
    if (!isActiveFlow.value && isActive) shouldLoadMore = true

    isActiveFlow.value = isActive
  }

  suspend fun itemSelected(itemWithIndex: Pair<Int, SeItemData>, isIndexOriginal: Boolean, modifiers: Int, searchText: String): Boolean {
    logItemSelectedEvent(itemWithIndex, isIndexOriginal)
    return tab.itemSelected(itemWithIndex.second, modifiers, searchText)
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

  suspend fun getEmptyResultInfo(context: DataContext): SeEmptyResultInfo? {
    return tab.getEmptyResultInfo(context)
  }

  suspend fun canBeShownInFindResults(): Boolean {
    return tab.canBeShownInFindResults()
  }

  suspend fun openInFindWindow(sessionRef: DurableRef<SeSessionEntity>, initEvent: AnActionEvent): Boolean {
    val params = SeParams(searchPattern.value, (filterEditor.getValue()?.resultFlow?.value ?: SeFilterState.Empty))
    return tab.openInFindToolWindow(sessionRef, params, initEvent)
  }

  suspend fun getSearchEverywhereToggleAction(): SearchEverywhereToggleAction? {
    return tab.getFilterEditor()?.getActions()?.firstOrNull {
      it is SearchEverywhereToggleAction
    } as? SearchEverywhereToggleAction
  }

  private fun StateFlow<String>.withNewValueSideEffect(sideEffect: suspend (String, String) -> Unit): Flow<String> =
    scan(Pair("", "")) { acc, new -> Pair(acc.second, new) }
      .drop(1)
      .map { (old, new) ->
        sideEffect(old, new)
        new
      }
}