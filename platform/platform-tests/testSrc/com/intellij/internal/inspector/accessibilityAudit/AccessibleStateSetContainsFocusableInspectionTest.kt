// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.swing.JButton
import javax.swing.JProgressBar

class AccessibleStateSetContainsFocusableInspectionTest {

  @Test
  fun `valid role and no focusable in state set`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean {
        return true
      }

      override fun isVisible(): Boolean {
        return true
      }

      override fun isEnabled(): Boolean {
        return true
      }
    }

    button.isFocusable = false
    val result = AccessibleStateSetContainsFocusableInspection().passesInspection(button.accessibleContext)

    Assertions.assertFalse(result)
  }

  @Test
  fun `valid role and focusable in state set`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean {
        return true
      }

      override fun isVisible(): Boolean {
        return true
      }

      override fun isEnabled(): Boolean {
        return true
      }
    }

    button.isFocusable = true
    val result = AccessibleStateSetContainsFocusableInspection().passesInspection(button.accessibleContext)

    Assertions.assertTrue(result)
  }

  @Test
  fun `invalid role`() {
    val bar = JProgressBar()
    val result = AccessibleStateSetContainsFocusableInspection().passesInspection(bar.accessibleContext)

    Assertions.assertTrue(result)
  }

}