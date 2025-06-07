// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.platform.searchEverywhere.frontend.ui.SePopupContentPane
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

/**
 * Accumulate results until DEFAULT_RESULT_THROTTLING_MS has past or DEFAULT_RESULT_COUNT_TO_STOP_THROTTLING items where received,
 * then send the first batch of items inside SeThrottledAccumulatedItems, and then send on demand one by one SeThrottledOneItem.
 */
@OptIn(DelicateCoroutinesApi::class)
@ApiStatus.Internal
fun <T> Flow<T>.throttledWithAccumulation(): Flow<SeThrottledItems<T>> {
  val originalFlow = this
  return channelFlow {
    var pendingFirstBatch: MutableList<T>? = mutableListOf<T>() // null -> no more throttling
    val mutex = Mutex()
    suspend fun sendFirstBatchIfNeeded() {
      mutex.withLock {
        if (isClosedForSend) return

        val firstBatch = pendingFirstBatch
        pendingFirstBatch = null
        if (!firstBatch.isNullOrEmpty()) {
          send(SeThrottledAccumulatedItems(firstBatch))
        }
      }
    }
    launch {
      delay(SePopupContentPane.DEFAULT_RESULT_THROTTLING_MS)
      sendFirstBatchIfNeeded()
    }
    launch {
      originalFlow.collect { item ->
        mutex.withLock {
          val firstBatch = pendingFirstBatch
          if (firstBatch != null) {
            firstBatch += item
            if (firstBatch.size >= SePopupContentPane.DEFAULT_RESULT_COUNT_TO_STOP_THROTTLING) {
              pendingFirstBatch = null
              send(SeThrottledAccumulatedItems(firstBatch))
            }
          }
          else {
            send(SeThrottledOneItem(item))
          }
        }
      }
      sendFirstBatchIfNeeded()
      mutex.withLock {
        close()
      }
    }
  }.buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
}

@ApiStatus.Internal
sealed interface SeThrottledItems<T>
@ApiStatus.Internal
class SeThrottledAccumulatedItems<T>(val items: List<T>) : SeThrottledItems<T>
@ApiStatus.Internal
class SeThrottledOneItem<T>(val item: T) : SeThrottledItems<T>
