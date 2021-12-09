// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ProgressCoroutineTest : LightPlatformTestCase() {

  private fun backgroundActivity(indicator: ProgressIndicator, action: () -> Unit): Future<*> {
    return AppExecutorUtil.getAppExecutorService().submit {
      ProgressManager.getInstance().runProcess(action, indicator)
    }
  }

  fun `test suspending action job is a child of current job`() {
    val job = Job()
    withJob(job) {
      runBlockingCancellable {
        assertJobIsChildOf(job = coroutineContext.job, parent = job)
      }
    }
  }

  fun `test indicator cancellation cancels job`() {
    val lock = Semaphore(1)
    val indicator = EmptyProgressIndicator()
    val future = backgroundActivity(indicator) {  // some blocking code under indicator
      assertThrows(ProcessCanceledException::class.java) {
        runBlockingCancellable {                     // want to switch to coroutine world from under the blocking code
          ensureActive()
          lock.up()
          while (true) {    // never-ending suspending action
            ensureActive()  // not actually needed since delay() checks for cancellation
            delay(500)
          }
        }
      }
    }
    assertTrue(lock.waitFor(2000))
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
          Thread.sleep(1)
        }
      }
    }
    assertTrue(lock.waitFor(2000))
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
      assertTrue(lock.waitFor(2000))
      try {
        deferred.await()
        fail("PCE expected")
      }
      catch (e: ProcessCanceledException) {
      }
    }
  }

  fun `test indicator text via progress sink`() {
    suspend fun xx() = progressSink()?.fraction(0.42)
    val indicator = object : EmptyProgressIndicator() {
      var myText: String? = null
      var myText2: String? = null
      var myFraction: Double? = null

      override fun setText(text: String) {
        myText = text
      }

      override fun setText2(text: String) {
        myText2 = text
      }

      override fun setFraction(fraction: Double) {
        myFraction = fraction
      }
    }
    backgroundActivity(indicator) {
      runBlockingCancellable {
        progressSink?.text("Hello")
        progressSink?.details("World")
        xx()
      }
    }.get(2000, TimeUnit.MILLISECONDS)
    assertEquals(indicator.myText, "Hello")
    assertEquals(indicator.myText2, "World")
    assertEquals(indicator.myFraction, 0.42)
  }

  fun `test sink via progress indicator`() = runBlocking {
    val sink = object : ProgressSink {
      var text: String? = null
      var details: String? = null
      var fraction: Double? = null

      override fun text(text: String) {
        this.text = text
      }

      override fun details(details: String) {
        this.details = details
      }

      override fun fraction(fraction: Double) {
        this.fraction = fraction
      }
    }
    withContext(sink.asContextElement()) {
      runUnderIndicator {
        ProgressManager.progress("Hello", "World")
        ProgressManager.getInstance().progressIndicator.fraction = 0.42
      }
    }
    assertEquals(sink.text, "Hello")
    assertEquals(sink.details, "World")
    assertEquals(sink.fraction, 0.42)
  }

  fun `test checkCancelledEvenWithPCEDisabled checks job`() {
    val started = Semaphore(1)
    val canCheck = Semaphore(1)
    val job = Job()
    val f = ApplicationManager.getApplication().executeOnPooledThread {
      withJob(job) {
        checkCancelledEvenWithPCEDisabled(null)
        started.up()
        assertTrue(canCheck.waitFor(2000))
        try {
          checkCancelledEvenWithPCEDisabled(null)
          fail()
        }
        catch (ignored: JobCanceledException) {
        }
      }
    }
    assertTrue(started.waitFor(2000))
    job.cancel()
    canCheck.up()
    f.get(2000, TimeUnit.MILLISECONDS)
  }
}
