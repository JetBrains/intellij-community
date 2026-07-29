// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf

import com.intellij.openapi.application.UI
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class ElfTest {

  @Test
  fun `scope is visible only while task runs`() = runOnUi {
    assertFalse(isInElfScope())
    var taskPerformed = false
    withElfScope {
      assertTrue(isInElfScope())
      taskPerformed = true
    }
    assertTrue(taskPerformed)
    assertFalse(isInElfScope())
  }

  @Test
  fun `nested scopes keep scope active until outer task finishes`() = runOnUi {
    withElfScope {
      assertTrue(isInElfScope())
      withElfScope {
        assertTrue(isInElfScope())
      }
      assertTrue(isInElfScope())
    }
    assertFalse(isInElfScope())
  }

  @Test
  fun `scope is restored after exception`()= runOnUi {
    val exception = assertFailsWith<IllegalStateException> {
      withElfScope {
        assertTrue(isInElfScope())
        throw IllegalStateException("boom")
      }
    }
    assertEquals("boom", exception.message)
    assertFalse(isInElfScope())
  }

  @Test
  fun `unsupported operation guard is inactive outside elf scope`() = runOnUi {
    assertFalse(isUnsupportedOperationGuardActive())
  }

  @Test
  fun `unsupported operation guard is active inside elf scope`() = runOnUi {
    assertFalse(isUnsupportedOperationGuardActive())
    withElfScope {
      assertTrue(isUnsupportedOperationGuardActive())
    }
    assertFalse(isUnsupportedOperationGuardActive())
  }

  private fun runOnUi(action: () -> Unit) {
    timeoutRunBlocking(context = Dispatchers.UI) {
      action()
    }
  }

  private fun isUnsupportedOperationGuardActive(): Boolean {
    return Elf.getElf().isUnsupportedOperationGuardActive()
  }

  private fun isInElfScope(): Boolean {
    return Elf.getElf().isInElfScope()
  }

  private fun withElfScope(action: Runnable) {
    Elf.getElf().withElfScope {
      action.run()
    }
  }
}
