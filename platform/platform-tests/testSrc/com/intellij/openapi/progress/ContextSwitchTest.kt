// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.*
import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

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
  fun `blocking context`() {
    blockingContextTest {
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
    testPrepareThreadContext(blockingTest)
    testCancellableReadAction(blockingTest)
    testRunBlockingCancellable(coroutineTest)
  }

  private fun testPrepareThreadContext(blockingTest: () -> Unit) {
    prepareThreadContextTest {
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
      assertEquals(coroutineContext.job, Cancellation.currentJob())
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
    coroutineToIndicator {
      assertNotNull(Cancellation.currentJob())
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

  @Test
  fun `blockingContext into runBlockingCancellable`(): Unit = timeoutRunBlocking {
    val testElement: CoroutineContext = TestElement("xx")

    fun assertThreadContext() {
      val tc = currentThreadContext()
      assertSame(tc[TestElementKey], testElement)
      assertNull(tc[ContinuationInterceptor.Key])
    }

    withContext(testElement) {
      assertNull(currentThreadOverriddenContextOrNull())
      blockingContext {
        assertThreadContext()
        runBlockingCancellable {
          assertNull(currentThreadOverriddenContextOrNull())
          withContext(Dispatchers.Default) {
            blockingContext {
              assertThreadContext()
            }
          }
          assertNull(currentThreadOverriddenContextOrNull())
        }
        assertThreadContext()
      }
      assertNull(currentThreadOverriddenContextOrNull())
    }
  }
}
