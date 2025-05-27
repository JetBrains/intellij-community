// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleValue
import javax.swing.JButton
import javax.swing.JProgressBar


class AccessibleValueNotNullInspectionTest {
  @Test
  fun `valid role and value not null`() {
    val bar = JProgressBar()
    val result = AccessibleValueNotNullInspection().passesInspection(bar)
    Assertions.assertTrue(result)
  }

  @Test
  fun `invalid role and value not null`() {
    val button = JButton()
    val result = AccessibleValueNotNullInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }

  @Test
  fun `valid role and null value`() {
    val bar = object : JProgressBar() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PROGRESS_BAR

          override fun getAccessibleValue(): AccessibleValue? = null

        }
      }
    }
    val result = AccessibleValueNotNullInspection().passesInspection(bar)
    Assertions.assertFalse(result)
  }

}