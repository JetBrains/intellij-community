// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean

@TestApplication
class WriteIntentReadActionTest {

  @Test
  fun `fail-fast write intent read action can proceed if no actions are running`() = timeoutRunBlocking {
    val marker = AtomicBoolean(false)
    assertTrue(application.threadingSupport.tryRunWriteIntentReadAction {
      marker.set(true)
    })
    assertTrue(marker.get())
  }

  @Test
  fun `fail-fast write intent read action can not proceed if there is a background write action`(): Unit = timeoutRunBlocking {
    val job = Job(coroutineContext.job)
    val canFinish = Job(coroutineContext.job)
    launch {
      backgroundWriteAction {
        job.complete()
        canFinish.asCompletableFuture().join()
      }
    }
    job.join()
    assertFalse(application.threadingSupport.tryRunWriteIntentReadAction {
      fail()
    })
    canFinish.complete()
  }

}