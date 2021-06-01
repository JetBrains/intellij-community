// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore

class CoroutineSuspenderTest : LightPlatformTestCase() {

  fun `test cancel paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Semaphore(count, count)
    val suspender = coroutineSuspender(false)
    val job = launch(Dispatchers.Default + suspender) {
      repeat(count) {
        launch {
          started.release()
          checkCanceled()
          fail("must not be called")
        }
      }
    }
    withTimeout(1000) {
      started.acquire() // all coroutines are started
    }
    letBackgroundThreadsSuspend()
    withTimeout(1000) {
      job.cancelAndJoin()
    }
  }

  fun `test resume paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Semaphore(count, count)
    val paused = Semaphore(count, count)
    val suspender = coroutineSuspender()
    val result = async(Dispatchers.Default + suspender) {
      (1..count).map {
        async { // coroutine context (including CoroutineSuspender) is inherited
          checkCanceled() // won't suspend
          started.release()
          paused.acquire()
          checkCanceled() // should suspend here
          it
        }
      }.awaitAll().sum()
    }
    withTimeout(1000) {
      started.acquire() // all coroutines are started
    }
    suspender.pause() // pause suspender before next checkCanceled
    repeat(count) {
      paused.release() // let coroutines pause in next checkCanceled
    }
    letBackgroundThreadsSuspend()
    val children = result.children.toList()
    assertSize(count, children)
    assertFalse(children.any { it.isCompleted })
    suspender.resume()
    withTimeout(1000) {
      assertEquals(55, result.await())
    }
  }

  private suspend fun letBackgroundThreadsSuspend(): Unit = delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
}
