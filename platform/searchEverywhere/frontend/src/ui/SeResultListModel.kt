// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.rpc.ThrottledAccumulatedItems
import com.intellij.ide.rpc.ThrottledItems
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.frontend.SeSearchStatePublisher
import com.intellij.platform.searchEverywhere.providers.SeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel

@ApiStatus.Internal
class SeResultListModel(private val searchStatePublisher: SeSearchStatePublisher,
                        private val selectionModelProvider: () -> ListSelectionModel): DefaultListModel<SeResultListRow>() {
  val freezer: Freezer = Freezer { size }

  val isValid: Boolean get() = isValidState.value
  val isValidState: StateFlow<Boolean> get() = _isValidState.asStateFlow()
  private val _isValidState = MutableStateFlow(true)

  fun reset() {
    SeLog.log(SeLog.THROTTLING) { "Will reset result list model" }
    freezer.reset()
    _isValidState.value = true
    removeAllElements()
  }

  fun invalidate() {
    _isValidState.value = false
  }

  fun removeLoadingItem() {
    if (size > 0 && getElementAt(size - 1) is SeResultListMoreRow) {
      removeElementAt(size - 1)
    }
  }

  fun addFromThrottledEvent(searchId: String, throttledEvent: ThrottledItems<SeResultEvent>) {
    if (!isValid) reset()

    when (throttledEvent) {
      is ThrottledAccumulatedItems<SeResultEvent> -> {
        val accumulatedList = SeResultListCollection()
        throttledEvent.items.forEach {
          accumulatedList.handleEvent(it)
        }
        addAll(accumulatedList.list)

        accumulatedList.list.filterIsInstance<SeResultListItemRow>().takeIf { it.isNotEmpty() }?.map {
          it.item
        }?.let { items ->
          searchStatePublisher.elementsAdded(searchId, items.associateBy { it.uuid })
        }
      }
      is ThrottledOneItem<SeResultEvent> -> {
        SeResultListModelAdapter(this, selectionModelProvider()).handleEvent(throttledEvent.item, onAdd = {
          searchStatePublisher.elementsAdded(searchId, mapOf(it.uuid to it))
        }, onRemove = {
          searchStatePublisher.elementsRemoved(searchId, 1)
        })
      }
    }
  }

  class Freezer(private val listSize: () -> Int) {
    private var frozenCountToApply: Int = 0
    var isEnabled: Boolean = false
      private set

    val frozenCount: Int get() = if (isEnabled) frozenCountToApply else 0

    fun enable() {
      isEnabled = true
      SeLog.log(SeLog.FROZEN_COUNT) { "frozenCount = $frozenCountToApply; size = ${listSize()}; isApplied = $isEnabled" }
    }

    fun freezeIfEnabled(count: Int) {
      if (count > frozenCountToApply) {
        frozenCountToApply = count
        SeLog.log(SeLog.FROZEN_COUNT) { "frozenCount = $frozenCountToApply; size = ${listSize()}; isApplied = $isEnabled" }
      }
    }

    fun reset() {
      isEnabled = false
      frozenCountToApply = 0
    }
  }
}