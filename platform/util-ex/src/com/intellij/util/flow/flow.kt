// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.flow

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Returns a cold flow, which emits values of [this] flow not more often than the given [timeout][timeMs].
 *
 * Example:
 * ```kotlin
 * flow {
 *   delay(40)
 *   emit(1) // first value is always emitted
 *   delay(40)
 *   emit(2) // skipped because the emission happened within the timeout from the first emission
 *   delay(40)
 *   emit(3) // emitted as the latest value after the timeout from the first emission
 *   delay(40)
 *   emit(4) // last value is emitted after the given timeout
 * }.throttle(100)
 * ```
 * produces the following emissions
 * ```text
 * 1, 3, 4
 * ```
 */
fun <X> Flow<X>.throttle(timeMs: Long): Flow<X> {
  if (timeMs <= 0) {
    return this
  }
  return channelFlow {
    val latch = Channel<Unit>()
    val latchJob = launch(start = CoroutineStart.UNDISPATCHED) {
      while (isActive) {
        latch.send(Unit)
        delay(timeMs)
      }
    }
    try {
      collectLatest {
        latch.receive()
        @Suppress("EXPERIMENTAL_API_USAGE")
        send(it)
      }
    }
    finally {
      latchJob.cancel()
      latch.close()
    }
  }
}
