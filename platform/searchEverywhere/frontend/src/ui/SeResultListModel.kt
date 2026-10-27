// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.rpc.ThrottledAccumulatedItems
import com.intellij.ide.rpc.ThrottledItems
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeSearchStatePublisher
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.vm.SeSearchContext
import com.intellij.platform.searchEverywhere.providers.SeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel

@Internal
class SeResultListModel(private val searchStatePublisher: SeSearchStatePublisher,
                        private val selectionModelProvider: () -> ListSelectionModel): DefaultListModel<SeResultListRow>() {
  val freezer: Freezer = Freezer { size }

  val isValid: Boolean get() = isValidState.value
  val isValidState: StateFlow<Boolean> get() = _isValidState.asStateFlow()
  private val _isValidState = MutableStateFlow(true)

  val pendingReplacementElementUuids: MutableSet<String> = mutableSetOf()

  fun reset() {
    SeLog.log(SeLog.THROTTLING) { "Will reset result list model" }
    freezer.reset()
    _isValidState.value = true
    pendingReplacementElementUuids.clear()
    removeAllElements()
  }

  fun invalidate() {
    SeLog.log(SeLog.THROTTLING) { "Will invalidate result list model" }
    _isValidState.value = false
    freezer.reset()
  }

  fun removeLoadingItem() {
    if (size > 0 && getElementAt(size - 1) is SeResultListMoreRow) {
      removeElementAt(size - 1)
    }
  }

  fun addFromThrottledEvent(searchContext: SeSearchContext, throttledEvent: ThrottledItems<SeResultEvent>, isZeroOffset: Boolean) {
    var shouldFreezeImmediately = false

    if (!isValid) {
      reset()

      // For non-all tab right after receiving first accumulated items
      // we should freeze the list immediately, because results flickers on every typed symbol otherwise
      shouldFreezeImmediately = (searchContext.tabId != SeAllTab.ID) && throttledEvent is ThrottledAccumulatedItems

      if (shouldFreezeImmediately) {
        SeLog.log(SeLog.FROZEN_COUNT) { "Will freeze immediately (searchId = ${searchContext.searchId})" }
      }
    }

    val wasEmpty = isEmpty

    val resultListAdapter = SeResultListModelAdapter(this, selectionModelProvider())
    val onAdd = { item: SeItemData -> searchStatePublisher.elementsAdded(searchContext.searchId, mapOf(item.uuid to item)) }
    val onRemove = { searchStatePublisher.elementsRemoved(searchContext.searchId, 1) }
    when {
      // The list is valid and has rows, so each item must be sorted with the rows that are already in the list.
      throttledEvent is ThrottledOneItem<SeResultEvent> || !wasEmpty -> {
        throttledEvent.items.forEach {
          resultListAdapter.handleEvent(searchContext, it, isZeroOffset, onAdd, onRemove)
        }
      }
      else -> {
        val accumulatedList = SeResultListCollection(pendingReplacementElementUuids)
        throttledEvent.items.forEach {
          accumulatedList.handleEvent(searchContext, it, isZeroOffset)
        }

        addAll(accumulatedList.list)
        SeLog.log(SeLog.THROTTLING) {
          "Added batch of throttled events: ${accumulatedList.list.size}; Providers:" +
          accumulatedList.list.mapNotNull { (it as? SeResultListItemRow)?.item?.providerId?.value }.groupingBy { it }.eachCount().map {
            "${it.key} - ${it.value}"
          }.joinToString(", ")
        }

        accumulatedList.list.filterIsInstance<SeResultListItemRow>().takeIf { it.isNotEmpty() }?.map {
          it.item
        }?.let { items ->
          searchStatePublisher.elementsAdded(searchContext.searchId, items.associateBy { it.uuid })
        }
      }
    }

    if (shouldFreezeImmediately) {
      freezer.enable()
      freezer.freezeIfEnabled(size)
    }
  }

  class Freezer(private val listSize: () -> Int) {
    private var frozenCountToApply: Int = 0
    var isEnabled: Boolean = false
      private set

    val frozenCount: Int get() = if (isEnabled) frozenCountToApply else 0

    fun enable() {
      isEnabled = true
      SeLog.log(SeLog.FROZEN_COUNT) { "enable: frozenCount = $frozenCountToApply; size = ${listSize()}; isApplied = $isEnabled" }
    }

    fun freezeIfEnabled(count: Int) {
      if (count > frozenCountToApply) {
        frozenCountToApply = count
        SeLog.log(SeLog.FROZEN_COUNT) { "freezeIfEnabled: frozenCount = $frozenCountToApply; size = ${listSize()}; isApplied = $isEnabled" }
      }
    }

    fun reset() {
      isEnabled = false
      frozenCountToApply = 0
      SeLog.log(SeLog.FROZEN_COUNT) { "reset: frozenCount = $frozenCountToApply; size = ${listSize()}; isApplied = $isEnabled" }
    }
  }
}
