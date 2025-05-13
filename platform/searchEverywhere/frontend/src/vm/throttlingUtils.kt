// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.vm

import com.intellij.platform.searchEverywhere.frontend.ui.SePopupContentPane
import com.intellij.platform.searchEverywhere.providers.SeLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@Suppress("LocalVariableName")
@ApiStatus.Internal
inline fun <reified T> Flow<T>.throttledWithAccumulation(): Flow<SeThrottledItems<T>> {
  val THROTTLING_IN_PROGRESS = 0
  val NO_THROTTLING = 1
  val SHOULD_SEND_THROTTLED_AND_CLOSE = 2

  val originalFlow = this
  return channelFlow {
    val throttledEvents = mutableListOf<T>()
    val throttlingStatusFlow = MutableStateFlow(THROTTLING_IN_PROGRESS)

    launch {
      delay(SePopupContentPane.DEFAULT_RESULT_THROTTLING_MS)
      throttlingStatusFlow.value = NO_THROTTLING
    }

    val originalWithUnthrottlingOnCompletion = originalFlow.onCompletion {
      SeLog.log(SeLog.THROTTLING) { "Original flow completed" }
      throttlingStatusFlow.value = SHOULD_SEND_THROTTLED_AND_CLOSE
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)

    var lastCollectedStopThrottlingCount = THROTTLING_IN_PROGRESS

    merge(flowOf(1), originalWithUnthrottlingOnCompletion).buffer(0, onBufferOverflow = BufferOverflow.SUSPEND).combine(throttlingStatusFlow) { event, throttlingStatus ->
      event to throttlingStatus
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND).collect { (event, throttlingStatus) ->
      if (lastCollectedStopThrottlingCount != throttlingStatus) {
        if (throttlingStatus != THROTTLING_IN_PROGRESS) {
          if (throttlingStatus == NO_THROTTLING) {
            SeLog.log(SeLog.THROTTLING) { "Will send accumulated" }
          }

          if (throttledEvents.isNotEmpty()) {
            //SeLog.log(SeLog.THROTTLING) { "Will send accumulated events ${throttledEvents.size}" }
            send(SeThrottledAccumulatedItems(throttledEvents.toList()))
            throttledEvents.clear()
          }

          if (throttlingStatus == SHOULD_SEND_THROTTLED_AND_CLOSE) {
            SeLog.log(SeLog.THROTTLING) { "Will close channel" }
            close()
          }
        }

        lastCollectedStopThrottlingCount = throttlingStatus
      }
      else if (event is T) {
        if (throttlingStatus == THROTTLING_IN_PROGRESS) {
          SeLog.log(SeLog.THROTTLING) { "Will accumulate event (${throttledEvents.size})" }
          throttledEvents.add(event)

          if (throttledEvents.size >= SePopupContentPane.DEFAULT_RESULT_COUNT_TO_STOP_THROTTLING) {
            throttlingStatusFlow.update {
              if (it == THROTTLING_IN_PROGRESS) NO_THROTTLING
              else it
            }
          }
        }
        else {
          send(SeThrottledOneItem(event))
        }
      }
    }
  }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
}

@ApiStatus.Internal
sealed interface SeThrottledItems<T>
@ApiStatus.Internal
class SeThrottledAccumulatedItems<T>(val items: List<T>) : SeThrottledItems<T>
@ApiStatus.Internal
class SeThrottledOneItem<T>(val item: T) : SeThrottledItems<T>
