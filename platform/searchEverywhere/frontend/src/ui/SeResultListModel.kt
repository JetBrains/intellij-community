// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.topHit.SeTopHitItemsProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListModel

@ApiStatus.Internal
class SeResultListModel: DefaultListModel<SeResultListRow>() {
  val freezer: Freezer = Freezer { size }

  private val prioritizedProviders: List<SeProviderId> = listOfNotNull(
    "CalculatorSEContributor",
    "AutocompletionContributor",
    "CommandsContributor",
    SeTopHitItemsProvider.Companion.id(false),
    SeTopHitItemsProvider.Companion.id(true),
    if (AdvancedSettings.Companion.getBoolean("search.everywhere.recent.at.top")) RecentFilesSEContributor::class.java.getSimpleName() else null
  ).map { SeProviderId(it) }

  private val prioritizedProvidersPriorities: Map<SeProviderId, Int> = prioritizedProviders.withIndex().associate {
    it.value to ( prioritizedProviders.size - it.index)
  }

  fun reset() {
    freezer.reset()
    removeAllElements()
  }

  fun removeLoadingItem() {
    if (size > 0 && getElementAt(size - 1) is SeResultListMoreRow) {
      removeElementAt(size - 1)
    }
  }

  fun addFromEvent(event: SeResultEvent) {
    when (event) {
      is SeResultAddedEvent -> {
        val index = indexToAdd(event.itemData)
        add(index, SeResultListItemRow(event.itemData))

        /* Animated icon in the text field disappears when the first result appears.
         * So let the loading row will be in the list from the moment the first
         * item appears until the last item appears.
         */
        if (size == 1) {
          addElement(SeResultListMoreRow)
        }
      }
      is SeResultReplacedEvent -> {
        val index = firstIndexOrNull(true) { event.oldItemData == it }
                    ?: if (ApplicationManager.getApplication().isInternal) {
                      error("Item ${event.oldItemData} is not found in the list")
                    }
                    else null

        index?.takeIf {  it >= freezer.frozenCount }?.let { indexToRemove ->
          removeElementAt(indexToRemove)
          // Replace item and keep the same index for now
          val index = indexToRemove
          add(index, SeResultListItemRow(event.newItemData))
        }
      }
      is SeResultSkippedEvent -> null
    }
  }

  private fun indexToAdd(newItem: SeItemData): Int {
    return firstIndexOrNull(false) { item ->
      val newItemProviderPriority = prioritizedProvidersPriorities[newItem.providerId] ?: 0
      val itemProviderPriority = prioritizedProvidersPriorities[item.providerId] ?: 0

      if (newItemProviderPriority == itemProviderPriority) {
        newItem.weight > item.weight
      }
      else {
        newItemProviderPriority > itemProviderPriority
      }
    } ?: lastIndexToInsertItem
  }

  private fun firstIndexOrNull(fullSearch: Boolean, predicate: (SeItemData) -> Boolean): Int? {
    val startIndex = if (fullSearch) 0 else freezer.frozenCount

    return (startIndex until size).firstOrNull { index ->
      when (val row = getElementAt(index)) {
        is SeResultListItemRow -> {
          predicate(row.item)
        }
        SeResultListMoreRow -> false
      }
    }
  }

  private val lastIndexToInsertItem: Int get() =
    if (size == 0) 0
    else if (lastElement() is SeResultListMoreRow) size - 1
    else size

  class Freezer(private val listSize: () -> Int) {
    private var frozenCountToApply: Int = 0
    private var isApplied = false

    val frozenCount: Int get() = if (isApplied) frozenCountToApply else 0

    fun applyFreeze() {
      isApplied = true
    }

    fun freezeIfApplied(count: Int) {
      if (count > frozenCountToApply) {
        frozenCountToApply = count
        SeLog.Companion.log(SeLog.FROZEN_COUNT) { "frozenCount = $frozenCountToApply; size = ${listSize()}; isApplied = $isApplied" }
      }
    }

    fun freezeAllIfApplied() {
      freezeIfApplied(listSize())
    }

    fun reset() {
      isApplied = false
      frozenCountToApply = 0
    }
  }

  companion object {
    const val DEFAULT_FROZEN_COUNT: Int = 10
    const val DEFAULT_FREEZING_DELAY_MS: Long = 800
  }
}