// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class CancellationTest {

  @Rule
  @JvmField
  val application: ApplicationRule = ApplicationRule()

  private val TIMEOUT_MS: Long = 1000

  private fun neverEndingStory(): Nothing {
    while (true) {
      ProgressManager.checkCanceled()
      Thread.sleep(1)
    }
  }

  @Test
  fun `job cancellation makes checkCanceled throw CE`() {
    val lock = Semaphore(1)
    val job = Job()
    val future = AppExecutorUtil.getAppExecutorService().submit {
      withJob(job) {
        lock.up()
        neverEndingStory()
      }
    }
    assertTrue(lock.waitFor(TIMEOUT_MS))
    job.cancel()
    try {
      future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
      fail("ExecutionException expected")
    }
    catch (e: ExecutionException) {
      assertInstanceOf(e.cause, CancellationException::class.java)
    }
  }
}
