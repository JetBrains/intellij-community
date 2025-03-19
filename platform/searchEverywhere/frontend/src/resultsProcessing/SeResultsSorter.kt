// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.resultsProcessing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.frontend.SeTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeResultsSorter(private val tab: SeTab) {
  private val mutex = Mutex()

  fun getItems(params: SeParams): Flow<SeSortedResultEvent> {
    val items = mutableListOf<SeItemData>()

    return tab.getItems(params).mapNotNull {
      mutex.withLock {
        when (val event = it) {
          is SeResultAddedEvent -> {
            val index = items.indexOfFirst { event.itemData.weight > it.weight }.takeIf { it != -1 } ?: items.size
            items.add(index, event.itemData)
            SeSortedResultAddedEvent(event.itemData, index)
          }
          is SeResultReplacedEvent -> {
            items.indexOf(event.oldItemData).takeIf { it != -1 }?.let { indexToRemove ->
              items.removeAt(indexToRemove)
              // Replace item and keep the same index for now
              val index = indexToRemove
              items.add(index, event.newItemData)
              SeSortedResultReplacedEvent(event.newItemData, indexToRemove, index)
            } ?: if (ApplicationManager.getApplication().isInternal) {
              error("Item ${event.oldItemData} is not found in the list")
            } else null
          }
          is SeResultSkippedEvent -> null
        }
      }
    }
  }
}

@Internal
sealed interface SeSortedResultEvent {
  val itemData: SeItemData
  val index: Int
}

@Internal
class SeSortedResultAddedEvent(override val itemData: SeItemData, override val index: Int) : SeSortedResultEvent
@Internal
class SeSortedResultReplacedEvent(override val itemData: SeItemData, val indexToRemove: Int, override val index: Int) : SeSortedResultEvent
