// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.UI
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.useBlockingEdtWriteActionImplementation
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class EdtWriteActionTest {

  @BeforeEach
  fun `check that edtWriteAction implementation is non-blocking`() {
    Assumptions.assumeFalse(useBlockingEdtWriteActionImplementation, "This test is only for non-blocking implementation of edtWriteAction")
  }

  @Test
  fun `edtWriteAction does not block the UI thread`(): Unit = concurrencyTest {
    launch {
      runReadActionBlocking {
        checkpoint(1)
        checkpoint(6)
      }
    }
    launch {
      checkpoint(2)
      edtWriteAction {
        checkpoint(7)
      }
      checkpoint(8)
    }
    delay(100.milliseconds)
    checkpoint(3)
    withContext(Dispatchers.UI) {
      checkpoint(4)
    }
    checkpoint(5)
  }

  @Test
  fun `blocking read action on EDT proceeds despite suspended edtWriteAction`(): Unit =
    repeat(100) {
      `blocking action on EDT proceeds despite suspended edtWriteAction` {
        runReadActionBlocking(it::run)
      }
    }


  @Test
  fun `blocking write-intent read action on EDT proceeds despite suspended edtWriteAction`(): Unit =
    repeat(100) {
      `blocking action on EDT proceeds despite suspended edtWriteAction` {
        WriteIntentReadAction.run(it::run)
      }
    }

    @Test
  fun `blocking write action on EDT proceeds despite suspended edtWriteAction`(): Unit = timeoutRunBlocking {
    // stress test that there is no starvation due to suspension
    repeat(100) {
      launch {
        edtWriteAction {}
      }
      launch(Dispatchers.UiWithModelAccess) {
        WriteAction.run<Throwable> {  }
      }
    }
  }

  fun `blocking action on EDT proceeds despite suspended edtWriteAction`(edtAction: (Runnable) -> Unit): Unit = concurrencyTest {
    launch {
      runReadActionBlocking {
        checkpoint(1)
        checkpoint(8)
      }
    }
    launch {
      checkpoint(2)
      edtWriteAction {
        checkpoint(9)
      }
      checkpoint(10)
    }
    delay(30.milliseconds)
    checkpoint(3)
    withContext(Dispatchers.UiWithModelAccess) {
      checkpoint(4)
      edtAction {
        checkpoint(5)
      }
      checkpoint(6)
    }
    checkpoint(7)
  }
}
