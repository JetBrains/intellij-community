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
      override fun isShowing(): Boolean = true

      override fun isVisible(): Boolean = true

      override fun isEnabled(): Boolean = true
    }

    button.isFocusable = false
    val result = AccessibleStateSetContainsFocusableInspection().passesInspection(button)

    Assertions.assertFalse(result)
  }

  @Test
  fun `valid role and focusable in state set`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean = true
    }
    button.isEnabled = true
    button.isVisible = true
    button.isFocusable = true
    val result = AccessibleStateSetContainsFocusableInspection().passesInspection(button)

    Assertions.assertTrue(result)
  }

  @Test
  fun `invalid role`() {
    val bar = JProgressBar()
    val result = AccessibleStateSetContainsFocusableInspection().passesInspection(bar)

    Assertions.assertTrue(result)
  }

}