// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities

@TestApplication
class SwingThreadingTest {

  @Test
  fun `swing invokeLater does not capture any lock`() = timeoutRunBlocking {
    val job = Job(coroutineContext.job)
    SwingUtilities.invokeLater {
      try {
        Assertions.assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
        Assertions.assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed)
        Assertions.assertFalse(ApplicationManager.getApplication().isWriteIntentLockAcquired)
        job.complete()
      }
      catch (t: Throwable) {
        job.completeExceptionally(t)
      }
    }
    job.join()
  }

  @Test
  fun `swing invokeAndWait does not capture any lock`() = timeoutRunBlocking {
    SwingUtilities.invokeAndWait {
      Assertions.assertFalse(ApplicationManager.getApplication().isReadAccessAllowed)
      Assertions.assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed)
      Assertions.assertFalse(ApplicationManager.getApplication().isWriteIntentLockAcquired)
    }
  }
}