// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.*
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ProgressCoroutineTest : LightPlatformTestCase() {

  private inline fun <reified T : Throwable> assertThrows(noinline action: () -> Unit) {
    assertThrows(T::class.java, action)
  }

  private fun backgroundActivity(indicator: ProgressIndicator, action: () -> Unit): Future<*> {
    return AppExecutorUtil.getAppExecutorService().submit {
      ProgressManager.getInstance().runProcess(action, indicator)
    }
  }

  fun `test indicator cancellation cancels job`() {
    val lock = Semaphore(1)
    val indicator = EmptyProgressIndicator()
    val future = backgroundActivity(indicator) {  // some blocking code under indicator
      assertThrows(ProcessCanceledException::class.java) {
        runSuspendingAction {                     // want to switch to coroutine world from under the blocking code
          ensureActive()
          lock.up()
          while (true) {    // never-ending suspending action
            ensureActive()  // not actually needed since delay() checks for cancellation
            delay(500)
          }
        }
      }
    }
    lock.waitFor()
    indicator.cancel()
    future.get(2000, TimeUnit.MILLISECONDS)
  }

  fun `test job cancellation cancels indicator`(): Unit = runBlocking {
    val lock = Semaphore(1)
    val job = launch(Dispatchers.Default) {   // some coroutine
      runUnderIndicator {                     // want to execute blocking Java code from under coroutine
        ProgressManager.checkCanceled()       // this Java code doesn't know about Job inside, so runUnderIndicator runs it under indicator
        lock.up()
        while (true) { // never-ending blocking action
          ProgressManager.checkCanceled()
          Thread.sleep(500)
        }
      }
    }
    lock.waitFor()
    withTimeout(2000) {
      job.cancelAndJoin()
    }
  }

  fun `test PCE from runUnderIndicator is rethrown`(): Unit = runBlocking {
    val lock = Semaphore(1)
    supervisorScope {
      val deferred: Deferred<Unit> = async(Dispatchers.Default) {
        runUnderIndicator {
          lock.up()
          throw ProcessCanceledException()
        }
      }
      lock.waitFor()
      try {
        deferred.await()
        fail("PCE expected")
      }
      catch (e: ProcessCanceledException) {
      }
    }
  }
}
