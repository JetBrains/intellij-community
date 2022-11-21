// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.ModalityStateEx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CoroutineToIndicatorTest : CancellationTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    assertNull(Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())

    val modality = ModalityStateEx()

    withContext(modality.asContextElement()) {
      assertSame(ModalityState.NON_MODAL, ModalityState.defaultModalityState())
      coroutineToIndicator {
        assertNull(Cancellation.currentJob())
        assertNotNull(ProgressManager.getGlobalProgressIndicator())
        assertSame(modality, ModalityState.defaultModalityState())
      }
      assertSame(ModalityState.NON_MODAL, ModalityState.defaultModalityState())
    }

    assertNull(Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())
  }

  @Test
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        coroutineToIndicator {
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
      coroutineToIndicator {
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

      override fun update(text: String?, details: String?, fraction: Double?) {
        if (text != null) {
          this.text = text
        }
        if (details != null) {
          this.details = details
        }
        if (fraction != null) {
          this.fraction = fraction
        }
      }
    }

    withContext(sink.asContextElement()) {
      coroutineToIndicator {
        ProgressManager.progress("Hello", "World")
        ProgressManager.getInstance().progressIndicator.fraction = 0.42
      }
    }

    assertEquals(sink.text, "Hello")
    assertEquals(sink.details, "World")
    assertEquals(sink.fraction, 0.42)
  }
}
