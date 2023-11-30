// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CancellableReadActionWithJobTest : CancellableReadActionTests() {

  @Test
  fun context() {
    blockingContextTest {
      val currentJob = checkNotNull(Cancellation.currentJob())
      val application = ApplicationManager.getApplication()

      assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessNotAllowed()

      val result = computeCancellable {
        val readJob = requireNotNull(Cancellation.currentJob())
        assertJobIsChildOf(job = readJob, parent = currentJob)
        assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertReadAccessAllowed()
        42
      }
      assertEquals(42, result)

      assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessNotAllowed()
    }
  }

  @Test
  fun cancellation() {
    blockingContextTest {
      assertThrows<ProcessCanceledException> {
        computeCancellable {
          testNoExceptions()
          checkNotNull(Cancellation.currentJob()).cancel()
          testExceptions()
        }
      }
    }
  }

  @Test
  fun rethrow() {
    blockingContextTest {
      testComputeCancellableRethrow()
    }
  }

  @Test
  fun `throws when a write is pending`() {
    blockingContextTest {
      testThrowsIfPendingWrite()
    }
  }

  @Test
  fun `throws when a write is running`() {
    blockingContextTest {
      testThrowsIfRunningWrite()
    }
  }

  @Test
  fun `does not throw when a write is requested during almost finished computation`() {
    blockingContextTest {
      testDoesntThrowWhenAlmostFinished()
    }
  }

  @Test
  fun `throws when a write is requested during computation`() {
    blockingContextTest {
      testThrowsOnWrite()
    }
  }

  @Test
  fun `throws inside non-cancellable read action when a write is requested during computation`() {
    blockingContextTest {
      runReadAction {
        testThrowsOnWrite()
      }
    }
  }
}
