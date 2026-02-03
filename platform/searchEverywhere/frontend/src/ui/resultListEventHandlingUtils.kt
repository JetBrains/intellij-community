// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.actions.searcheverywhere.RecentFilesSEContributor
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.vm.SeSearchContext
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
fun SeResultList.handleEvent(searchContext: SeSearchContext, event: SeResultEvent, onAdd: ((SeItemData) -> Unit)? = null, onRemove: (() -> Unit)? = null) {
  when (event) {
    is SeResultAddedEvent -> {
      if (pendingReplacementElementUuids.remove(event.itemData.uuid)) {
        SeLog.log(SeLog.DEFAULT) { "SeResultAddedEvent: uuid ${event.itemData.uuid} was skipped because it was supposed to be replaced by an element which came earlier" }
      }
      else {
        val index = indexToAdd(event.itemData, searchContext.searchPattern)
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
        val index = firstIndexOrNull(true) { uuidToReplace == it.uuid }
        if (index == null) {
          pendingReplacementElementUuids.add(uuidToReplace)
          SeLog.log(SeLog.DEFAULT) { "SeResultReplacedEvent: uuid $uuidToReplace not found in the list, saved to pending replacement" }
        }

        index
      }.sortedDescending()

      if (indexes.isEmpty()) {
        val index = indexToAdd(event.newItemData, searchContext.searchPattern)
        addRow(index, SeResultListItemRow(event.newItemData))
        onAdd?.invoke(event.newItemData)
      }
      else {
        indexes.forEach { index ->
          removeRow(index)
          onRemove?.invoke()
          if (index == indexes.last()) {
            // We replace only one element with the smallest index
            addRow(index, SeResultListItemRow(event.newItemData))
            onAdd?.invoke(event.newItemData)
          }
        }
      }
    }
    is SeResultEndEvent -> {} // Do nothing
  }
}

private fun SeResultList.indexToAdd(newItem: SeItemData, searchPattern: String): Int {
  if (newItem.isCommand) {
    val firstNotCommandIndex = firstIndexOrNull(true, true) { item -> !item.isCommand } ?: size

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

  return firstIndexOrNull(false) { item ->
    if (item.isCommand) return@firstIndexOrNull false

    val newItemProviderPriority = SeResultList.prioritizedProvidersPriorities[newItem.providerId] ?: 0
    val itemProviderPriority = SeResultList.prioritizedProvidersPriorities[item.providerId] ?: 0

    if (newItemProviderPriority == itemProviderPriority) {
      newItem.weight > item.weight
    }
    else {
      newItemProviderPriority > itemProviderPriority
    }
  } ?: lastIndexToInsertItem
}

private fun SeResultList.firstIndexOrNull(fullSearch: Boolean, acceptMoreRow: Boolean = false, predicate: (SeItemData) -> Boolean): Int? {
  val startIndex = if (fullSearch) 0 else frozenCount

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