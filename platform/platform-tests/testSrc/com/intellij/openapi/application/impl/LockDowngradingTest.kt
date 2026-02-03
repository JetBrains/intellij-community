// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.progress.runBlockingCancellable
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
          Assertions.assertFalse(application.isWriteAccessAllowed)
          Assertions.assertTrue(application.isReadAccessAllowed)
        }
      }
    }
  }

  @Test
  fun `write-intent to read`() = timeoutRunBlocking {
    writeIntentReadAction {
      runBlockingCancellable {
        readAction {
          Assertions.assertFalse(application.isWriteAccessAllowed)
          Assertions.assertTrue(application.isReadAccessAllowed)
        }
      }
    }
  }
}