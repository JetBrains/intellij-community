// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*

class CoroutineSuspenderTest : LightPlatformTestCase() {

  fun `test cancel paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Semaphore(count)
    val suspender = coroutineSuspender(false)
    val job = launch(Dispatchers.Default + suspender) {
      repeat(count) {
        launch {
          started.up()
          checkCanceled()
          fail("must not be called")
        }
      }
    }
    assertTrue(started.waitFor(1000))
    withTimeout(1000) {
      job.cancelAndJoin()
    }
  }

  fun `test resume paused coroutines`(): Unit = runBlocking {
    val count = 10
    val started = Semaphore(count)
    val paused = Semaphore(1)
    val suspender = coroutineSuspender()
    val result = async(Dispatchers.Default + suspender) {
      (1..count).map {
        async { // coroutine context (including CoroutineSuspender) is inherited
          checkCanceled() // won't suspend
          started.up()
          assertTrue(paused.waitFor(1000))
          checkCanceled() // should suspend here
          it
        }
      }.awaitAll().sum()
    }
    assertTrue(started.waitFor(1000))
    suspender.pause()
    paused.up()
    delay(10) // letBackgroundThreadsSuspend
    val children = result.children.toList()
    assertSize(count, children)
    assertFalse(children.any { it.isCompleted })
    suspender.resume()
    withTimeout(1000) {
      assertEquals(55, result.await())
    }
  }
}
