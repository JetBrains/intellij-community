// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CancellableReadActionWithIndicatorTest : CancellableReadActionTests() {

  @Test
  fun context() {
    indicatorTest {
      val application = ApplicationManager.getApplication()

      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessNotAllowed()

      val result = computeCancellable {
        assertNotNull(Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertReadAccessAllowed()
        42
      }
      assertEquals(42, result)

      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
      application.assertReadAccessNotAllowed()
    }
  }

  @Test
  fun cancellation() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      assertThrows<ProcessCanceledException> {
        computeCancellable {
          testNoExceptions()
          indicator.cancel()
          requireNotNull(Cancellation.currentJob()).timeoutJoinBlocking()
          testExceptions()
        }
      }
    }
  }

  @Test
  fun rethrow() {
    indicatorTest {
      testComputeCancellableRethrow()
    }
  }

  @Test
  fun `throws when a write is pending`() {
    indicatorTest {
      testThrowsIfPendingWrite()
    }
  }

  @Test
  fun `throws when a write is running`() {
    indicatorTest {
      testThrowsIfRunningWrite()
    }
  }

  @Test
  fun `does not throw when a write is requested during almost finished computation`() {
    indicatorTest {
      testDoesntThrowWhenAlmostFinished()
    }
  }

  @Test
  fun `throws when a write is requested during computation`() {
    indicatorTest {
      testThrowsOnWrite()
    }
  }

  @Test
  fun `throws inside non-cancellable read action when a write is requested during computation`() {
    indicatorTest {
      runReadAction {
        testThrowsOnWrite()
      }
    }
  }
}
