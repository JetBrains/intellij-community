// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
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
  fun `job cancellation makes checkCanceled throw CE`() {
    val lock = Semaphore(1)
    val job = Job()
    val future = AppExecutorUtil.getAppExecutorService().submit {
      withJob(job) {
        lock.up()
        neverEndingStory()
      }
    }
    lock.waitUp()
    job.cancel()
    waitAssertCompletedWithCancellation(future)
  }
}
