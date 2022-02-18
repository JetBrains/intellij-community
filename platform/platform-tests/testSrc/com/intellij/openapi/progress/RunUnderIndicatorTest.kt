// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RunUnderIndicatorTest : CancellationTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    assertNull(Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())

    runUnderIndicator {
      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
    }

    assertNull(Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())
  }

  @Test
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        runUnderIndicator {
          ProgressManager.checkCanceled()
          coroutineContext.job.cancel()
          throw assertThrows<ProcessCanceledException> {
            ProgressManager.checkCanceled()
          }
        }
      }
    }
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    testRunUnderIndicatorRethrow(object : Throwable() {})
    testRunUnderIndicatorRethrow(CancellationException()) // manual CE
    testRunUnderIndicatorRethrow(ProcessCanceledException()) // manual PCE
  }

  private suspend inline fun <reified T : Throwable> testRunUnderIndicatorRethrow(t: T) {
    val thrown = assertThrows<T> {
      runUnderIndicator {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  @Test
  fun `delegates reporting to context sink`(): Unit = timeoutRunBlocking {
    val sink = object : ProgressSink {

      var text: String? = null
      var details: String? = null
      var fraction: Double? = null

      override fun text(text: String) {
        this.text = text
      }

      override fun details(details: String) {
        this.details = details
      }

      override fun fraction(fraction: Double) {
        this.fraction = fraction
      }
    }

    withContext(sink.asContextElement()) {
      runUnderIndicator {
        ProgressManager.progress("Hello", "World")
        ProgressManager.getInstance().progressIndicator.fraction = 0.42
      }
    }

    assertEquals(sink.text, "Hello")
    assertEquals(sink.details, "World")
    assertEquals(sink.fraction, 0.42)
  }
}
