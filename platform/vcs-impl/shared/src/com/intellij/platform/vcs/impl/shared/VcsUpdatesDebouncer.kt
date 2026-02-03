// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared

import com.intellij.platform.util.coroutines.flow.debounceBatch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
class VcsUpdatesDebouncer<T>(batchHandler: ProducerScope<T>.(batchUpdate: List<T>) -> Unit) {
  private val rawUpdates: MutableSharedFlow<T> =
    MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  val updates: Flow<T> =
    channelFlow {
      launch {
        rawUpdates.debounceBatch(100.milliseconds).collect { batchUpdate ->
          batchHandler(batchUpdate)
        }
      }
    }

  fun tryEmit(event: T): Boolean = rawUpdates.tryEmit(event)
}