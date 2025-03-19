// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.testFramework.UsefulTestCase.assertSize
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class CoroutineSuspenderTest {

  @Test
  fun `cancel paused coroutines`(): Unit = timeoutRunBlocking {
    val count = 10
    val started = Channel<Unit>()
    val suspender = coroutineSuspender(false)
    assertTrue(suspender.isPaused())
    val job = launch(Dispatchers.Default + suspender.asContextElement()) {
      repeat(count) {
        launch {
          started.send(Unit)
          checkCanceled()
          fail("must not be called")
        }
      }
    }
    repeat(count) {
      started.receive()
    }
    // all coroutines are started
    letBackgroundThreadsSuspend()
    job.cancelAndJoin()
  }

  @Test
  fun `resume paused coroutines`(): Unit = timeoutRunBlocking {
    val count = 10
    val started = Channel<Unit>()
    val paused = Channel<Unit>()
    val suspender = coroutineSuspender()
    assertFalse(suspender.isPaused())
    val result = async(Dispatchers.Default + suspender.asContextElement()) {
      (1..count).map {
        async { // coroutine context (including CoroutineSuspender) is inherited
          checkCanceled() // won't suspend
          started.send(Unit)
          paused.receive()
          checkCanceled() // should suspend here
          it
        }
      }.awaitAll().sum()
    }
    repeat(count) {
      started.receive()
    }
    // all coroutines are started
    suspender.pause() // pause suspender before next checkCanceled
    assertTrue(suspender.isPaused())
    repeat(count) {
      paused.send(Unit) // let coroutines pause in next checkCanceled
    }
    letBackgroundThreadsSuspend()
    val children = result.children.toList()
    assertSize(count, children)
    assertFalse(children.any { it.isCompleted })
    suspender.resume()
    assertEquals(55, result.await())
  }

  private suspend fun letBackgroundThreadsSuspend(): Unit = delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
}
