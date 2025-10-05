// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.concurrency.installThreadContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExistingThreadContextTest : CancellationTest() {

  @Test
  fun `without context`() {
    prepareThreadContext { prepared ->
      assertNull(currentThreadContextOrNull())
      assertSame(prepared, EmptyCoroutineContext)
    }
  }

  @Test
  fun context() {
    val tc = Dispatchers.Default + TestElement("e")
    installThreadContext(tc) {
      assertSame(tc, currentThreadContextOrNull())
      prepareThreadContext { prepared ->
        assertNull(currentThreadContextOrNull())
        assertSame(tc, prepared)
      }
      assertSame(tc, currentThreadContextOrNull())
    }
  }

  @Test
  fun cancellation() {
    val t = object : Throwable() {}
    val ce = assertThrows<CeProcessCanceledException> {
      installThreadContext(Job()) {
        throw assertThrows<CeProcessCanceledException> {
          prepareThreadContextTest { currentJob ->
            testNoExceptions()
            currentJob.cancel("", t)
            testExceptionsAndNonCancellableSection()
          }
        }
      }
    }
    assertSame(t, ce.cause.cause)
  }

  @Test
  fun rethrow() {
    blockingContextTest {
      testPrepareThreadContextRethrow()
    }
  }

  @Test
  fun `cancelled by child failure`() {
    val job = Job()
    val t = Throwable()
    val ce = assertThrows<ProcessCanceledException> {
      installThreadContext(job) {
        throw assertThrows<CeProcessCanceledException> {
          prepareThreadContextTest { currentJob ->
            testNoExceptions()
            Job(parent = currentJob).completeExceptionally(t)
            assertThrows<CeProcessCanceledException> {
              Cancellation.checkCancelled()
            }
            throw assertThrows<CeProcessCanceledException> {
              ProgressManager.checkCanceled()
            }
          }
        }
      }
    }
    assertSame(t, ce.cause?.cause)
    assertFalse(job.isActive)
    assertTrue(job.isCancelled)
    assertTrue(job.isCompleted)
  }
}
