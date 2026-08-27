// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseRunReadActionBlockingShortcut")

package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class LockDowngradingTest {

  @Test
  fun `write to read`() = timeoutRunBlocking {
    edtWriteAction {
      runBlockingCancellable {
        readAction {
          Assertions.assertFalse(application.isWriteAccessAllowed, "Write access must not be available inside a read action")
          Assertions.assertTrue(application.isReadAccessAllowed, "Read access must be available inside a read action")
        }
      }
    }
  }

  @Test
  fun `write-intent to read`() = timeoutRunBlocking {
    writeIntentReadAction {
      runBlockingCancellable {
        readAction {
          Assertions.assertFalse(application.isWriteAccessAllowed, "Write access must not be available inside a read action")
          Assertions.assertFalse(application.isWriteIntentLockAcquired, "Write-intent lock must remain acquired")
          Assertions.assertFalse(
            ApplicationManagerEx.getApplicationEx().isParallelizedReadAction(currentThreadContext()),
            "Read action must not be parallelized in runBlockingCancellable",
          )
          Assertions.assertTrue(application.isReadAccessAllowed, "Read access must be available inside a read action")
        }
      }
    }
  }

  @Test
  fun `write-intent to read in modal computation`() = timeoutRunBlocking {
    writeIntentReadAction {
      runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
        readAction {
          Assertions.assertFalse(application.isWriteAccessAllowed, "Write access must not be available inside a read action")
          Assertions.assertFalse(application.isWriteIntentLockAcquired, "Write-intent lock must not remain acquired")
          Assertions.assertFalse(
            ApplicationManagerEx.getApplicationEx().isParallelizedReadAction(currentThreadContext()),
            "Read action must not be parallelized inside a modal computation",
          )
          Assertions.assertTrue(application.isReadAccessAllowed, "Read access must be available inside a read action")
        }
      }
    }
  }

  @Test
  fun `write-intent to read outside modal computation`() = timeoutRunBlocking {
    writeIntentReadAction {
      ReadAction.runBlocking<Throwable> {
        runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
          Assertions.assertFalse(application.isWriteAccessAllowed, "Write access must not be available inside a read action")
          Assertions.assertFalse(application.isWriteIntentLockAcquired, "Write-intent lock must not remain acquired")
          Assertions.assertFalse(
            ApplicationManagerEx.getApplicationEx().isParallelizedReadAction(currentThreadContext()),
            "Read action must not be parallelized inside a modal computation",
          )
          Assertions.assertFalse(application.isReadAccessAllowed, "Read access must not be available inside parallelized write-intent")
        }
      }
    }
  }

  @Test
  fun `write-intent to read outside runBlockignCancellable`() = timeoutRunBlocking {
    writeIntentReadAction {
      application.runReadAction {
        runBlockingCancellable {
          Assertions.assertFalse(application.isWriteAccessAllowed, "Write access must not be available inside a read action")
          Assertions.assertTrue(application.isWriteIntentLockAcquired, "Write-intent lock is acquired as thread-local")
          Assertions.assertTrue(
            ApplicationManagerEx.getApplicationEx().isParallelizedReadAction(currentThreadContext()),
            "Read action must be parallelized",
          )
          Assertions.assertTrue(application.isReadAccessAllowed, "Read access must be available inside parallelized read")
        }
      }
    }
  }
}
