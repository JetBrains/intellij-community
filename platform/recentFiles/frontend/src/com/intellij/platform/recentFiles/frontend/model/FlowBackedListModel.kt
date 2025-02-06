// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModelUpdate.*
import com.intellij.ui.CollectionListModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

private val LOG by lazy { fileLogger() }

@ApiStatus.Internal
sealed interface FlowBackedListModelUpdate<Item> {
  class AllItemsRemoved<Item>() : FlowBackedListModelUpdate<Item>

  data class ItemAdded<Item>(val item: Item) : FlowBackedListModelUpdate<Item>

  data class ItemRemoved<Item>(val item: Item) : FlowBackedListModelUpdate<Item>
}

@ApiStatus.Internal
class FlowBackedListModel<Item>(
  private val coroutineScope: CoroutineScope,
  private val itemUpdatesFlow: Flow<FlowBackedListModelUpdate<Item>>,
) : CollectionListModel<Item>(), Disposable {

  init {
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      subscribeToBackendDataUpdates()
    }
  }

  private suspend fun subscribeToBackendDataUpdates() {
    LOG.debug("Started collecting updates from FlowBackedListModel")
    itemUpdatesFlow.collect { update ->
      LOG.debug("Received update in FlowBackedListModel: $update")
      when {
        update is ItemAdded && update.item != null -> {
          LOG.debug("Adding item ${update.item} to FlowBackedListModel")
          withContext(Dispatchers.EDT) { add(update.item) }
        }
        update is ItemRemoved && update.item != null -> {
          LOG.debug("Removing item ${update.item} from FlowBackedListModel")
          withContext(Dispatchers.EDT) { remove(update.item) }
        }
        update is AllItemsRemoved -> {
          LOG.debug("Removing all items from FlowBackedListModel")
          withContext(Dispatchers.EDT) { removeAll() }
        }
      }
    }
  }

  override fun dispose() {
    LOG.debug("Disposing FlowBackedListModel")
    coroutineScope.coroutineContext.cancelChildren(CancellationException("FlowBackedListModel is disposed"))
  }
}