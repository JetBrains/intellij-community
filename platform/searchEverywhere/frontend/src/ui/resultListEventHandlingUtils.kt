// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeResultAddedEvent
import com.intellij.platform.searchEverywhere.SeResultEndEvent
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeResultReplacedEvent
import com.intellij.platform.searchEverywhere.SeResultSkippedEvent
import com.intellij.platform.searchEverywhere.composedWeight
import com.intellij.platform.searchEverywhere.equalityProviders.platformProviderPriority
import com.intellij.platform.searchEverywhere.isCommand
import com.intellij.platform.searchEverywhere.isExactMatch
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.topHit.SeTopHitItemsProvider
import org.jetbrains.annotations.ApiStatus
import javax.swing.ListSelectionModel

@ApiStatus.Internal
interface SeResultList {
  val size: Int
  val frozenCount: Int
  val pendingReplacementElementUuids: MutableSet<String>

  fun getRow(index: Int): SeResultListRow
  fun addRow(index: Int, row: SeResultListRow)
  fun addRow(row: SeResultListRow)
  fun removeRow(index: Int)

  companion object {
    private val prioritizedProviders: List<SeProviderId> = listOfNotNull(
      "CalculatorSEContributor",
      "AutocompletionContributor",
      "CommandsContributor",
      if (AdvancedSettings.getBoolean("search.everywhere.recent.at.top")) RecentFilesSEContributor::class.java.getSimpleName() else null,
      SeTopHitItemsProvider.id(false),
      SeTopHitItemsProvider.id(true),
    ).map { SeProviderId(it) }

    val prioritizedProvidersPriorities: Map<SeProviderId, Int> = prioritizedProviders.withIndex().associate {
      it.value to (prioritizedProviders.size - it.index)
    }
  }
}

@ApiStatus.Internal
fun SeResultList.handleEvent(
  searchPattern: String,
  event: SeResultEvent,
  isZeroOffset: Boolean,
  onAdd: ((SeItemData) -> Unit)? = null,
  onRemove: (() -> Unit)? = null,
) {
  when (event) {
    is SeResultAddedEvent -> {
      if (pendingReplacementElementUuids.remove(event.itemData.uuid)) {
        SeLog.log(SeLog.DEFAULT) { "SeResultAddedEvent: uuid ${event.itemData.uuid} was skipped because it was supposed to be replaced by an element which came earlier" }
      }
      else {
        val index = indexToAdd(event.itemData, searchPattern, isZeroOffset)
        addRow(index, SeResultListItemRow(event.itemData))
        onAdd?.invoke(event.itemData)

        /* Animated icon in the text field disappears when the first result appears.
         * So let the loading row will be in the list from the moment the first
         * item appears until the last item appears.
         */
        if (size == 1) {
          addRow(SeResultListMoreRow)
        }
      }
    }
    is SeResultReplacedEvent -> {
      val indexes = event.uuidsToReplace.mapNotNull { uuidToReplace ->
        val index = firstIndexOrNull(0) { uuidToReplace == it.uuid }
        if (index == null) {
          pendingReplacementElementUuids.add(uuidToReplace)
          SeLog.log(SeLog.DEFAULT) { "SeResultReplacedEvent: uuid $uuidToReplace not found in the list, saved to pending replacement" }
        }

        index
      }.sortedDescending()

      if (indexes.isEmpty()) {
        val index = indexToAdd(event.newItemData, searchPattern, isZeroOffset)
        addRow(index, SeResultListItemRow(event.newItemData))
        onAdd?.invoke(event.newItemData)
      }
      else {
        // The new item goes to its place by the ordering key.
        // A frozen item keeps its place, which is the smallest index of the replaced items.
        val firstIndex = indexes.last()
        val isReinserted = firstIndex >= frozenCount

        indexes.forEach { index ->
          removeRow(index)
          onRemove?.invoke()
        }

        val index = if (isReinserted) indexToAdd(event.newItemData, searchPattern, isZeroOffset) else firstIndex
        addRow(index, SeResultListItemRow(event.newItemData))
        onAdd?.invoke(event.newItemData)
      }
    }
    is SeResultEndEvent, is SeResultSkippedEvent -> {} // Do nothing
  }
}

private fun SeResultList.indexToAdd(newItem: SeItemData, searchPattern: String, isZeroOffset: Boolean): Int {
  if (newItem.isCommand) {
    val firstNotCommandIndex = firstIndexOrNull(0, true) { item -> !item.isCommand } ?: size

    val comparator = compareBy<SeItemData>(
      { !it.presentation.text.lowercase().startsWith(searchPattern) },
      { it.presentation.text.lowercase() }
    )
    for (i in 0..<firstNotCommandIndex) {
      val row = getRow(i)
      if (row is SeResultListItemRow) {
        val item = row.item
        if (comparator.compare(newItem, item) < 0) {
          return i
        }
      }
    }

    return firstNotCommandIndex
  }

  val startIndex =
    if (isZeroOffset) {
      if (newItem.isExactMatch) 0
      else {
        // Insert new elements above the 0 or lower weighted items
        minOf(firstIndexOrNull(0) { it.weight <= 0 } ?: frozenCount, frozenCount)
      }
    }
    else frozenCount

  val newItemKey = newItem.orderingKey
  return firstIndexOrNull(startIndex) { item ->
    if (item.isCommand) return@firstIndexOrNull false

    val prioritizeExactMatch = Registry.`is`("search.everywhere.exact.match.priority", false) && newItem.providerId == item.providerId
    newItemKey.compare(item.orderingKey, prioritizeExactMatch) > 0
  } ?: lastIndexToInsertItem
}

/** The priority of the provider of this item in the result list. See [SeResultList.prioritizedProvidersPriorities]. */
private val SeItemData.providerMajorPriority: Int get() = SeResultList.prioritizedProvidersPriorities[providerId] ?: 0

/** The priority of the provider of this item among the platform providers. See [platformProviderPriority]. */
private val SeItemData.providerMinorPriority: Int get() = platformProviderPriority(providerId.value)

private val SeItemData.orderingKey: SeResultOrderingKey
  get() = SeResultOrderingKey(providerMajorPriority, isExactMatch, composedWeight, providerMinorPriority)

/**
 * The values that decide the place of an item in the result list.
 *
 * A test can build a key without the [SeItemData] of an item.
 */
internal class SeResultOrderingKey(
  val providerMajorPriority: Int,
  val isExactMatch: Boolean,
  val weight: SeComposedWeight,
  val providerMinorPriority: Int,
) {
  fun compare(other: SeResultOrderingKey, prioritizeExactMatch: Boolean): Int {
    if (providerMajorPriority != other.providerMajorPriority) return providerMajorPriority.compareTo(other.providerMajorPriority)
    if (prioritizeExactMatch && isExactMatch != other.isExactMatch) return isExactMatch.compareTo(other.isExactMatch)

    val weightOrder = weight.compareTo(other.weight)
    if (weightOrder != 0) return weightOrder

    return providerMinorPriority.compareTo(other.providerMinorPriority)
  }
}

private fun SeResultList.firstIndexOrNull(startIndex: Int = frozenCount, acceptMoreRow: Boolean = false, predicate: (SeItemData) -> Boolean): Int? {
  return (startIndex until size).firstOrNull { index ->
    when (val row = getRow(index)) {
      is SeResultListItemRow -> {
        predicate(row.item)
      }
      SeResultListMoreRow -> acceptMoreRow
    }
  }
}

val SeResultList.lastIndexToInsertItem: Int
  @ApiStatus.Internal
  get() =
    if (size == 0) 0
    else if (getRow(size - 1) is SeResultListMoreRow) size - 1
    else size

@ApiStatus.Internal
class SeResultListModelAdapter(private val listModel: SeResultListModel, private val selectionModel: ListSelectionModel) : SeResultList {
  override val size: Int get() = listModel.size
  override val frozenCount: Int get() = listModel.freezer.frozenCount
  override val pendingReplacementElementUuids: MutableSet<String>
    get() = listModel.pendingReplacementElementUuids

  override fun getRow(index: Int): SeResultListRow =
    listModel.getElementAt(index)

  override fun addRow(index: Int, row: SeResultListRow) {
    val selectedIndexes = selectionModel.selectedIndices

    listModel.add(index, row)

    val newSelectedIndex = if (selectedIndexes.size == 1) {
      if (selectedIndexes[0] == 0) 0
      else selectedIndexes[0] + (if (index <= selectedIndexes[0]) 1 else 0)
    }
    else if (selectedIndexes.size > 1 && selectedIndexes.any { index <= it }) 0
    else null

    newSelectedIndex?.let {
      selectionModel.setSelectionInterval(it, it)
    }
  }

  override fun addRow(row: SeResultListRow) {
    listModel.addElement(row)
  }

  override fun removeRow(index: Int) {
    listModel.remove(index)
  }
}

@ApiStatus.Internal
class SeResultListCollection(override val pendingReplacementElementUuids: MutableSet<String>) : SeResultList {
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