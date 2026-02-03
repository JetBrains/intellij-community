// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application

import com.intellij.openapi.application.TransactionGuard
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
class TransactionTestJunit5 {

  @Test
  fun `background is write-safe`(): Unit = timeoutRunBlocking {
    assertThat(EDT.isCurrentThreadEdt()).isFalse
    assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue
    val job = Job(coroutineContext[Job])
    SwingUtilities.invokeLater {
      application.executeOnPooledThread {
        assertThat(TransactionGuard.getInstance().isWritingAllowed).isTrue
        job.complete()
      }
    }
    job.join()
  }
}