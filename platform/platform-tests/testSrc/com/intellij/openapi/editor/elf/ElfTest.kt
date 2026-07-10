// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.elf

import com.intellij.openapi.application.EDT
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
  fun `scope is visible only while task runs`() {
    withElfFeatureFlagEnabledOnEdt {
      assertFalse(isInElfScope())
      var taskPerformed = false
      withElfScope {
        assertTrue(isInElfScope())
        taskPerformed = true
      }
      assertTrue(taskPerformed)
      assertFalse(isInElfScope())
    }
  }

  @Test
  fun `nested scopes keep scope active until outer task finishes`() {
    withElfFeatureFlagEnabledOnEdt {
      withElfScope {
        assertTrue(isInElfScope())
        withElfScope {
          assertTrue(isInElfScope())
        }
        assertTrue(isInElfScope())
      }
      assertFalse(isInElfScope())
    }
  }

  @Test
  fun `scope is restored after exception`() {
    withElfFeatureFlagEnabledOnEdt {
      val exception = assertFailsWith<IllegalStateException> {
        withElfScope {
          assertTrue(isInElfScope())
          throw IllegalStateException("boom")
        }
      }
      assertEquals("boom", exception.message)
      assertFalse(isInElfScope())
    }
  }

  @Test
  fun `psi interaction is allowed outside elf scope`() {
    withElfFeatureFlagEnabledOnEdt {
      assertTrue(isPsiInteractionAllowed())
    }
  }

  @Test
  fun `psi interaction is blocked inside elf scope`() {
    withElfFeatureFlagEnabledOnEdt {
      assertTrue(isPsiInteractionAllowed())
      withElfScope {
        assertFalse(isPsiInteractionAllowed())
      }
      assertTrue(isPsiInteractionAllowed())
    }
  }

  @Test
  fun `scope is not activated while feature flag is disabled`() {
    withElfFeatureFlagDisabledOnEdt {
      assertFalse(isInElfScope())
      var taskPerformed = false
      withElfScope {
        assertFalse(isInElfScope())
        taskPerformed = true
      }
      assertTrue(taskPerformed)
      assertFalse(isInElfScope())
    }
  }

  @Test
  fun `psi interaction stays allowed while feature flag is disabled`() {
    withElfFeatureFlagDisabledOnEdt {
      assertTrue(isPsiInteractionAllowed())
      withElfScope {
        assertFalse(isInElfScope())
        assertTrue(isPsiInteractionAllowed())
      }
      assertTrue(isPsiInteractionAllowed())
    }
  }

  private fun withElfFeatureFlagEnabledOnEdt(action: () -> Unit) {
    runOnEdt {
      ElfFeatureFlag.withEnabled {
        action()
      }
    }
  }

  private fun withElfFeatureFlagDisabledOnEdt(action: () -> Unit) {
    runOnEdt {
      val oldValue = ElfFeatureFlag.isEnabled()
      ElfFeatureFlag.setEnabled(false)
      try {
        action()
      } finally {
        ElfFeatureFlag.setEnabled(oldValue)
      }
    }
  }

  private fun runOnEdt(action: () -> Unit) {
    timeoutRunBlocking(context = Dispatchers.EDT) {
      action()
    }
  }

  private fun isPsiInteractionAllowed(): Boolean {
    return Elf.getElf().isPsiInteractionAllowed()
  }

  private fun isInElfScope(): Boolean {
    return Elf.getElf().isInElfScope()
  }

  private fun withElfScope(action: Runnable) {
    return Elf.getElf().withElfScope(action)
  }
}
