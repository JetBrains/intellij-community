// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.swing.JButton

class AccessibleNameNotEmptyForFocusableComponentsInspectionTest {

  @Test
  fun `stateSet is full and name is not null or empty`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean = true
    }
    button.accessibleContext.accessibleName = "name"
    button.isEnabled = true
    button.isVisible = true

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }

  @Test
  fun `stateSet is full and name is empty`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean = true
    }
    button.accessibleContext.accessibleName = ""
    button.isEnabled = true
    button.isVisible = true

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(button)
    Assertions.assertFalse(result)
  }

  @Test
  fun `stateSet is full and name is null`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean = true
    }
    button.accessibleContext.accessibleName = null
    button.isEnabled = true
    button.isVisible = true

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(button)
    Assertions.assertFalse(result)
  }

  @Test
  fun `no visible in stateSet and name not empty`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean = true
    }
    button.accessibleContext.accessibleName = "name"
    button.isEnabled = true
    button.isVisible = false

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }

  @Test
  fun `no focusable in stateSet and name not empty`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean = true
    }
    button.accessibleContext.accessibleName = "name"
    button.isEnabled = true
    button.isFocusable = false

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }

}
