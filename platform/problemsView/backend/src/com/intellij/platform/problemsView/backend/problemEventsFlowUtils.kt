// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.analysis.problemsView.toolWindow.splitApi.ProblemEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val BATCH_SIZE = 100
private const val DEBOUNCE_WINDOW = 50L

internal fun Flow<ProblemEvent>.batchEvents(
  maxBatchSize: Int = BATCH_SIZE,
  debounceWindowMs: Long = DEBOUNCE_WINDOW
): Flow<List<ProblemEvent>> = channelFlow {
  val eventBuffer = Channel<ProblemEvent>(Channel.UNLIMITED)

  launch {
    try {
      this@batchEvents.collect { event ->
        eventBuffer.send(event)
      }
    } finally {
      eventBuffer.close()
    }
  }

  while (true) {
    val batch = mutableListOf<ProblemEvent>()
    val firstEvent = eventBuffer.receiveCatching().getOrNull() ?: break
    batch += firstEvent

    while (batch.size < maxBatchSize) {
      batch += eventBuffer.tryReceive().getOrNull() ?: break
    }

    send(batch)

    if (batch.size < maxBatchSize) {
      delay(debounceWindowMs.milliseconds)
    }
  }
}