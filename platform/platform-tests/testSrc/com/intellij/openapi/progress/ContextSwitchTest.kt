// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ContextSwitchTest : CancellationTest() {

  @Test
  fun indicator() {
    indicatorTest {
      testBlocking {
        testCoroutine {
          testBlocking {}
        }
      }
    }
  }

  @Test
  fun `current job`() {
    currentJobTest {
      testBlocking {
        testCoroutine {
          testBlocking {}
        }
      }
    }
  }

  @Test
  fun coroutine(): Unit = timeoutRunBlocking {
    testCoroutine {
      testBlocking {
        testCoroutine {}
      }
    }
  }

  private fun testBlocking(coroutineTest: suspend () -> Unit) {
    testBlocking(coroutineTest) {
      testBlocking(coroutineTest) {}
    }
  }

  private fun testBlocking(coroutineTest: suspend () -> Unit, blockingTest: () -> Unit) {
    testEnsureCurrentJob(blockingTest)
    testCancellableReadAction(blockingTest)
    testRunBlockingCancellable(coroutineTest)
  }

  private fun testEnsureCurrentJob(blockingTest: () -> Unit) {
    ensureCurrentJob {
      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      blockingTest()
    }
  }

  private fun testCancellableReadAction(blockingTest: () -> Unit) {
    ReadAction.computeCancellable<Unit, Throwable> {
      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      blockingTest()
    }
  }

  private fun testRunBlockingCancellable(coroutineTest: suspend () -> Unit) {
    runBlockingCancellable {
      assertNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      coroutineTest()
    }
  }

  private suspend fun testCoroutine(blockingTest: () -> Unit) {
    testBlockingContext(blockingTest)
    testRunUnderIndicator(blockingTest)
    testReadAction(blockingTest)
    testReadActionBlocking(blockingTest)
  }

  private suspend fun testBlockingContext(blockingTest: () -> Unit) {
    blockingContext {
      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      blockingTest()
    }
  }

  private suspend fun testRunUnderIndicator(blockingTest: () -> Unit) {
    runUnderIndicator {
      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
      blockingTest()
    }
  }

  private suspend fun testReadAction(blockingTest: () -> Unit) {
    readAction {
      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      blockingTest()
    }
  }

  private suspend fun testReadActionBlocking(blockingTest: () -> Unit) {
    readActionBlocking {
      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
      blockingTest()
    }
  }
}
