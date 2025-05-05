// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.FROZEN_COUNT
import com.intellij.platform.searchEverywhere.providers.topHit.SeTopHitItemsProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListModel

@ApiStatus.Internal
class SeResultListModel: DefaultListModel<SeResultListRow>() {
  private var frozenCount: Int = 0
  var ignoreFreezing: Boolean = false

  private val prioritizedProviders: List<SeProviderId> = listOfNotNull(
    "CalculatorSEContributor",
    "AutocompletionContributor",
    "CommandsContributor",
    SeTopHitItemsProvider.id(false),
    SeTopHitItemsProvider.id(true),
    if (AdvancedSettings.getBoolean("search.everywhere.recent.at.top")) RecentFilesSEContributor::class.java.getSimpleName() else null
  ).map { SeProviderId(it) }

  private val prioritizedProvidersPriorities: Map<SeProviderId, Int> = prioritizedProviders.withIndex().associate {
    it.value to ( prioritizedProviders.size - it.index)
  }

  fun freeze(count: Int) {
    if (count > frozenCount) {
      frozenCount = count
      SeLog.log(FROZEN_COUNT) { "frozenCount = $frozenCount; size = $size; ignoreFreezing = $ignoreFreezing" }
    }
  }

  fun freezeAll() {
    freeze(size)
  }

  fun reset() {
    frozenCount = 0
    ignoreFreezing = true
    super.removeAllElements()
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

        index?.takeIf { ignoreFreezing || it >= frozenCount }?.let { indexToRemove ->
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
    val startIndex = if (fullSearch || ignoreFreezing) 0 else frozenCount

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

  companion object {
    const val DEFAULT_FROZEN_COUNT: Int = 10
    const val DEFAULT_FREEZING_DELAY_MS: Long = 800
  }
}