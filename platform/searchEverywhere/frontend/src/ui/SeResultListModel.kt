// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.FROZEN_COUNT
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListModel

@ApiStatus.Internal
class SeResultListModel: DefaultListModel<SeResultListRow>() {
  private var frozenCount: Int = 0
  var ignoreFreezing: Boolean = false

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
        val index = firstIndexOrNull(false) { event.itemData.weight > it.weight } ?: lastIndexToInsertItem
        add(index, SeResultListItemRow(event.itemData))
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