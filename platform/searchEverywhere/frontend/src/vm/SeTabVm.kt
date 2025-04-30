// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.frontend.utils.suspendLazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class SeTabVm(
  project: Project,
  coroutineScope: CoroutineScope,
  private val tab: SeTab,
  searchPattern: StateFlow<String>,
) {
  val searchResults: StateFlow<Flow<SeResultListEvent>> get() = _searchResults.asStateFlow()
  val name: String get() = tab.name
  val filterEditor: SuspendLazyProperty<SeFilterEditor?> = suspendLazy { tab.getFilterEditor() }
  val tabId: String get() = tab.id

  fun filterEditorOrNull(): SeFilterEditor? = filterEditor.getValueOrNull()

  private val shouldLoadMoreFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
  var shouldLoadMore: Boolean
    get() = shouldLoadMoreFlow.value
    set(value) { shouldLoadMoreFlow.value = value }

  private val _searchResults: MutableStateFlow<Flow<SeResultListEvent>> = MutableStateFlow(emptyFlow())
  private val isActiveFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
  private val dumbModeStateFlow = MutableStateFlow(DumbService.isDumb(project)).also {
    project.messageBus.connect(coroutineScope).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun enteredDumbMode() { it.value = true }
      override fun exitDumbMode() { it.value = false }
    })
  }


  init {
    coroutineScope.launch {
      isActiveFlow.combine(dumbModeStateFlow) { isActive, _ ->
        isActive
      }.collectLatest { isActive ->
        if (!isActive) return@collectLatest

        combine(searchPattern, filterEditor.getValue()?.resultFlow ?: flowOf(null)) { searchPattern, filterData ->
          Pair(searchPattern, filterData ?: SeFilterState.Empty)
        }.mapLatest { (searchPattern, filterData) ->
          val params = SeParams(searchPattern, filterData)

          flow {
            tab.getItems(params).map { item ->
              shouldLoadMoreFlow.first { it }
              item
            }.onCompletion {
              emit(SeResultListStopEvent)
            }.collect {
              emit(SeResultListUpdateEvent(it))
            }
          }
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

  suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean {
    return tab.itemSelected(item, modifiers, searchText)
  }
}