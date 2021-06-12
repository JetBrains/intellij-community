// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Progress
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runUnderIndicator
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Progress2IndicatorTest : LightPlatformTestCase() {

  fun `test progress cancellation cancels indicator`() {
    val lock = Semaphore(1)
    val cancelled = AtomicBoolean(false)
    val progress = Progress {
      cancelled.get()
    }
    val future = AppExecutorUtil.getAppExecutorService().submit {
      runUnderIndicator(progress) {
        ProgressManager.checkCanceled()
        lock.up()
        while (true) { // never-ending blocking action
          ProgressManager.checkCanceled()
          Thread.sleep(1)
        }
      }
    }
    assertTrue(lock.waitFor(2000))
    cancelled.set(true)
    try {
      future.get(2000, TimeUnit.MILLISECONDS)
      fail("CE expected")
    }
    catch (e: ExecutionException) {
      assertInstanceOf(e.cause, CancellationException::class.java)
    }
  }

  fun `test PCE from runUnderIndicator is rethrown`() {
    val progress = Progress {
      false
    }
    val future = AppExecutorUtil.getAppExecutorService().submit {
      runUnderIndicator(progress) {
        throw ProcessCanceledException()
      }
    }
    try {
      future.get(2000, TimeUnit.MILLISECONDS)
      fail("PCE expected")
    }
    catch (e: ExecutionException) {
      assertInstanceOf(e.cause, ProcessCanceledException::class.java)
    }
  }

  fun `test CE from runUnderIndicator is rethrown`() {
    val progress = Progress {
      false
    }
    val future = AppExecutorUtil.getAppExecutorService().submit {
      runUnderIndicator(progress) {
        throw CancellationException()
      }
    }
    try {
      future.get(2000, TimeUnit.MILLISECONDS)
      fail("CE expected")
    }
    catch (e: ExecutionException) {
      assertInstanceOf(e.cause, CancellationException::class.java)
    }
  }
}
