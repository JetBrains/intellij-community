// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.checkCancelled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.openapi.progress.timeoutAwait
import com.intellij.openapi.progress.timeoutJoin
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class CoroutineSuspenderTest : LightPlatformTestCase() {

  fun `test cancel paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Channel<Unit>()
    val suspender = coroutineSuspender(false)
    assertTrue(suspender.isPaused())
    val job = launch(Dispatchers.Default + suspender) {
      repeat(count) {
        launch {
          started.send(Unit)
          checkCancelled()
          fail("must not be called")
        }
      }
    }
    repeat(count) {
      started.receive()
    }
    // all coroutines are started
    letBackgroundThreadsSuspend()
    job.cancel()
    job.timeoutJoin()
  }

  fun `test resume paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Channel<Unit>()
    val paused = Channel<Unit>()
    val suspender = coroutineSuspender()
    assertFalse(suspender.isPaused())
    val result = async(Dispatchers.Default + suspender) {
      (1..count).map {
        async { // coroutine context (including CoroutineSuspender) is inherited
          checkCancelled() // won't suspend
          started.send(Unit)
          paused.receive()
          checkCancelled() // should suspend here
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
    assertEquals(55, result.timeoutAwait())
  }

  private suspend fun letBackgroundThreadsSuspend(): Unit = delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
}
