// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend.model

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.recentFiles.frontend.model.FlowBackedListModelUpdate.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.CollectionListModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import org.jetbrains.annotations.ApiStatus

private val LOG by lazy { fileLogger() }

@ApiStatus.Internal
sealed interface FlowBackedListModelUpdate<Item> {
  class AllItemsRemoved<Item>() : FlowBackedListModelUpdate<Item>

  data class ItemAdded<Item>(val item: Item) : FlowBackedListModelUpdate<Item>

  data class ItemRemoved<Item>(val item: Item) : FlowBackedListModelUpdate<Item>

  class UpdateCompleted<Item>() : FlowBackedListModelUpdate<Item>
}

@ApiStatus.Internal
enum class FlowBackedListModelState {
  CREATED, LOADING, LOADED, DISPOSED
}

@ApiStatus.Internal
class FlowBackedListModel<Item>(
  private val coroutineScope: CoroutineScope,
  private val itemUpdatesFlow: Flow<FlowBackedListModelUpdate<Item>>,
) : CollectionListModel<Item>(), Disposable {

  private val modelUpdateState = MutableStateFlow(FlowBackedListModelState.CREATED)

  init {
    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
      subscribeToBackendDataUpdates()
    }
  }


  suspend fun awaitModelPopulation(durationMillis: Long) {
    val fastPreloadedModelAttempt = withTimeoutOrNull(durationMillis) {
      modelUpdateState.firstOrNull { state -> state == FlowBackedListModelState.LOADED }
    }
    LOG.debug("Switcher fast model preload attempt ${if (fastPreloadedModelAttempt != null) "succeeded" else "failed"} in $durationMillis ms")
  }

  private suspend fun subscribeToBackendDataUpdates() {
    LOG.debug("Started collecting updates from FlowBackedListModel")
    itemUpdatesFlow.collect { update ->
      LOG.debug("Received update in FlowBackedListModel: $update")
      when {
        update is ItemAdded && update.item != null -> {
          LOG.debug("Adding item ${update.item} to FlowBackedListModel")
          modelUpdateState.value = FlowBackedListModelState.LOADING
          withContext(Dispatchers.EDT) { add(update.item) }
        }
        update is ItemRemoved && update.item != null -> {
          LOG.debug("Removing item ${update.item} from FlowBackedListModel")
          modelUpdateState.value = FlowBackedListModelState.LOADING
          withContext(Dispatchers.EDT) { remove(update.item) }
        }
        update is AllItemsRemoved -> {
          LOG.debug("Removing all items from FlowBackedListModel")
          modelUpdateState.value = FlowBackedListModelState.LOADING
          withContext(Dispatchers.EDT) { removeAll() }
        }
        update is UpdateCompleted -> {
          LOG.debug("FlowBackedListModel update is finished")
          modelUpdateState.value = FlowBackedListModelState.LOADED
        }
      }
    }
  }

  override fun dispose() {
    LOG.debug("Disposing FlowBackedListModel")
    modelUpdateState.value = FlowBackedListModelState.DISPOSED
    coroutineScope.cancel(CancellationException("FlowBackedListModel is disposed"))
  }
}