// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.topHit.SeTopHitItemsProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SeResultList {
  val size: Int
  val frozenCount: Int

  fun getRow(index: Int): SeResultListRow
  fun addRow(index: Int, row: SeResultListRow)
  fun addRow(row: SeResultListRow)
  fun removeRow(index: Int)

  companion object {
    private val prioritizedProviders: List<SeProviderId> = listOfNotNull(
      "CalculatorSEContributor",
      "AutocompletionContributor",
      "CommandsContributor",
      SeTopHitItemsProvider.id(false),
      SeTopHitItemsProvider.id(true),
      if (AdvancedSettings.getBoolean("search.everywhere.recent.at.top")) RecentFilesSEContributor::class.java.getSimpleName() else null
    ).map { SeProviderId(it) }

    val prioritizedProvidersPriorities: Map<SeProviderId, Int> = prioritizedProviders.withIndex().associate {
      it.value to (prioritizedProviders.size - it.index)
    }
  }
}

@ApiStatus.Internal
fun SeResultList.handleEvent(event: SeResultEvent) {
  when (event) {
    is SeResultAddedEvent -> {
      val index = indexToAdd(event.itemData)
      addRow(index, SeResultListItemRow(event.itemData))

      /* Animated icon in the text field disappears when the first result appears.
       * So let the loading row will be in the list from the moment the first
       * item appears until the last item appears.
       */
      if (size == 1) {
        addRow(SeResultListMoreRow)
      }
    }
    is SeResultReplacedEvent -> {
      val index = firstIndexOrNull(true) { event.oldItemData == it }
                  ?: if (ApplicationManager.getApplication().isInternal) {
                    error("Item ${event.oldItemData} is not found in the list")
                  }
                  else null

      index?.takeIf { it >= frozenCount }?.let { indexToRemove ->
        removeRow(indexToRemove)
        // Replace item and keep the same index for now
        val index = indexToRemove
        addRow(index, SeResultListItemRow(event.newItemData))
      }
    }
    is SeResultSkippedEvent -> null
  }
}

private fun SeResultList.indexToAdd(newItem: SeItemData): Int {
  return firstIndexOrNull(false) { item ->
    val newItemProviderPriority = SeResultList.Companion.prioritizedProvidersPriorities[newItem.providerId] ?: 0
    val itemProviderPriority = SeResultList.Companion.prioritizedProvidersPriorities[item.providerId] ?: 0

    if (newItemProviderPriority == itemProviderPriority) {
      newItem.weight > item.weight
    }
    else {
      newItemProviderPriority > itemProviderPriority
    }
  } ?: lastIndexToInsertItem
}

private fun SeResultList.firstIndexOrNull(fullSearch: Boolean, predicate: (SeItemData) -> Boolean): Int? {
  val startIndex = if (fullSearch) 0 else frozenCount

  return (startIndex until size).firstOrNull { index ->
    when (val row = getRow(index)) {
      is SeResultListItemRow -> {
        predicate(row.item)
      }
      SeResultListMoreRow -> false
    }
  }
}

private val SeResultList.lastIndexToInsertItem: Int
  get() =
    if (size == 0) 0
    else if (getRow(size - 1) is SeResultListMoreRow) size - 1
    else size

@ApiStatus.Internal
class SeResultListModelAdapter(private val list: SeResultListModel) : SeResultList {
  override val size: Int get() = list.size
  override val frozenCount: Int get() = list.freezer.frozenCount

  override fun getRow(index: Int): SeResultListRow =
    list.getElementAt(index)

  override fun addRow(index: Int, row: SeResultListRow) {
    list.add(index, row)
  }

  override fun addRow(row: SeResultListRow) {
    list.addElement(row)
  }

  override fun removeRow(index: Int) {
    list.remove(index)
  }
}

@ApiStatus.Internal
class SeResultListCollection : SeResultList {
  val list: MutableList<SeResultListRow> = mutableListOf()

  override val size: Int get() = list.size
  override val frozenCount: Int get() = 0

  override fun getRow(index: Int): SeResultListRow =
    list[index]

  override fun addRow(index: Int, row: SeResultListRow) {
    list.add(index, row)
  }

  override fun addRow(row: SeResultListRow) {
    list.add(row)
  }

  override fun removeRow(index: Int) {
    list.removeAt(index)
  }
}