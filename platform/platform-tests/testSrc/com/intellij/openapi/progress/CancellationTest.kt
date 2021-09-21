// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import junit.framework.TestCase.fail
import kotlinx.coroutines.Job
import org.junit.ClassRule
import org.junit.Test

class CancellationTest {

  companion object {

    @ClassRule
    @JvmField
    val application: ApplicationRule = ApplicationRule()
  }

  @Test
  fun `job cancellation`() {
    val pm = ProgressManager.getInstance()
    val lock = Semaphore(1)
    val job = Job()
    val cancelled = Semaphore(1)
    val future = AppExecutorUtil.getAppExecutorService().submit {
      withJob(job) {
        ProgressManager.checkCanceled()
        lock.up()
        cancelled.waitUp()
        assertCheckCanceledThrows()
        pm.executeNonCancelableSection {
          ProgressManager.checkCanceled()
        }
        assertCheckCanceledThrows()
      }
    }
    lock.waitUp()
    job.cancel()
    cancelled.up()
    waitAssertCompletedNormally(future)
  }

  private fun assertCheckCanceledThrows() {
    try {
      ProgressManager.checkCanceled()
      fail()
    }
    catch (e: JobCanceledException) {
    }
  }
}
