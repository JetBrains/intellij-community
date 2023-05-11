// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.*
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore

class CoroutineSuspenderTest : LightPlatformTestCase() {

  fun `test cancel paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Semaphore(count, count)
    val suspender = coroutineSuspender(false)
    assertTrue(suspender.isPaused())
    val job = launch(Dispatchers.Default + suspender) {
      repeat(count) {
        launch {
          started.release()
          checkCancelled()
          fail("must not be called")
        }
      }
    }
    started.timeoutAcquire() // all coroutines are started
    letBackgroundThreadsSuspend()
    job.cancel()
    job.timeoutJoin()
  }

  fun `test resume paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Semaphore(count, count)
    val paused = Semaphore(count, count)
    val suspender = coroutineSuspender()
    assertFalse(suspender.isPaused())
    val result = async(Dispatchers.Default + suspender) {
      (1..count).map {
        async { // coroutine context (including CoroutineSuspender) is inherited
          checkCancelled() // won't suspend
          started.release()
          paused.acquire()
          checkCancelled() // should suspend here
          it
        }
      }.awaitAll().sum()
    }
    started.timeoutAcquire() // all coroutines are started
    suspender.pause() // pause suspender before next checkCanceled
    assertTrue(suspender.isPaused())
    repeat(count) {
      paused.release() // let coroutines pause in next checkCanceled
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
