// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import com.intellij.platform.util.coroutines.flow.throttleLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleLatestTest {
  @Test
  fun `first value is deferred by delay`() = runTest {
    val result = mutableListOf<Int>()
    val job = launch {
      flow { emit(1) }.throttleLatest(100.milliseconds).toList(result)
    }
    advanceTimeBy(99.milliseconds)
    runCurrent()
    assertEquals(emptyList<Int>(), result, "Should not emit before delay elapses")

    advanceTimeBy(1.milliseconds)
    runCurrent()
    assertEquals(listOf(1), result)
    job.join()
  }

  @Test
  fun `rapid events within a single window emit only the latest`() = runTest {
    val result = mutableListOf<Int>()
    val job = launch {
      flow {
        emit(1)
        delay(20.milliseconds)
        emit(2)
        delay(20.milliseconds)
        emit(3)
      }.throttleLatest(100.milliseconds).toList(result)
    }
    advanceUntilIdle()
    assertEquals(listOf(3), result)
    job.join()
  }

  @Test
  fun `events spanning two windows emit latest per window`() = runTest {
    val result = mutableListOf<Int>()
    val job = launch {
      flow {
        delay(40.milliseconds)
        emit(1) // t=40, starts window [40..140]
        delay(40.milliseconds)
        emit(2) // t=80
        delay(40.milliseconds)
        emit(3) // t=120, latest when window expires at t=140
        delay(40.milliseconds)
        emit(4) // t=160, starts window [160..260]
      }.throttleLatest(100.milliseconds).toList(result)
    }

    advanceTimeBy(140.milliseconds)
    runCurrent()
    assertEquals(listOf(3), result, "First window should emit latest value (3)")

    advanceUntilIdle()
    assertEquals(listOf(3, 4), result, "Second window should emit last value (4)")
    job.join()
  }

  @Test
  fun `window restarts only on new event after idle gap`() = runTest {
    val result = mutableListOf<String>()
    val job = launch {
      flow {
        emit("A")          // t=0, starts window [0..100]
        delay(300.milliseconds) // long gap — well past 100ms window
        emit("B")          // t=300, starts window [300..400]
      }.throttleLatest(100.milliseconds).toList(result)
    }

    advanceTimeBy(100.milliseconds)
    runCurrent()
    assertEquals(listOf("A"), result, "A emitted after first 100ms window")

    advanceTimeBy(200.milliseconds) // t=300: B arrives but window not yet elapsed
    runCurrent()
    assertEquals(listOf("A"), result, "B not yet emitted — window just started")

    advanceTimeBy(100.milliseconds) // t=400: B's window elapses
    runCurrent()
    assertEquals(listOf("A", "B"), result, "B emitted after second 100ms window")
    job.join()
  }

  @Test
  fun `empty flow produces no emissions`() = runTest {
    val result = mutableListOf<Int>()
    val job = launch {
      flow<Int> {}.throttleLatest(100.milliseconds).toList(result)
    }
    advanceUntilIdle()
    assertEquals(emptyList<Int>(), result)
    job.join()
  }
}