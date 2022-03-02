// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class RunBlockingCancellableTest : CancellationTest() {

  @Test
  fun `with current job context`() {
    currentJobTest { job ->
      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())

      runBlockingCancellable {
        assertJobIsChildOf(job = coroutineContext.job, parent = job)
        assertNull(Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
      }

      assertNotNull(Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
    }
  }

  @Test
  fun `with indicator context`() {
    indicatorTest {
      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())

      runBlockingCancellable {
        assertNull(Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
      }

      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
    }
  }

  @Test
  fun `with indicator cancellation`() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      assertThrows<ProcessCanceledException> {
        runBlockingCancellable {
          assertDoesNotThrow {
            ensureActive()
          }
          indicator.cancel()
          awaitCancellation()
        }
      }
    }
  }

  @Test
  fun `with current job rethrows exceptions`() {
    currentJobTest {
      testRunBlockingCancellableRethrow()
    }
  }

  @Test
  fun `with indicator rethrows exceptions`() {
    indicatorTest {
      testRunBlockingCancellableRethrow()
    }
  }

  private fun testRunBlockingCancellableRethrow() {
    testRunBlockingCancellableRethrow(object : Throwable() {})
    testRunBlockingCancellableRethrow(CancellationException()) // manual CE
    testRunBlockingCancellableRethrow(ProcessCanceledException()) // manual PCE
  }

  private inline fun <reified T : Throwable> testRunBlockingCancellableRethrow(t: T) {
    val thrown = assertThrows<T> {
      runBlockingCancellable {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  @Test
  fun `delegates reporting to current indicator`() {
    val indicator = object : EmptyProgressIndicator() {

      var myText: String? = null
      var myText2: String? = null
      var myFraction: Double? = null

      override fun setText(text: String) {
        myText = text
      }

      override fun setText2(text: String) {
        myText2 = text
      }

      override fun setFraction(fraction: Double) {
        myFraction = fraction
      }
    }

    withIndicator(indicator) {
      runBlockingCancellable {
        progressSink?.text("Hello")
        progressSink?.details("World")
        progressSink()?.fraction(0.42)
      }
    }

    assertEquals(indicator.myText, "Hello")
    assertEquals(indicator.myText2, "World")
    assertEquals(indicator.myFraction, 0.42)
  }
}
