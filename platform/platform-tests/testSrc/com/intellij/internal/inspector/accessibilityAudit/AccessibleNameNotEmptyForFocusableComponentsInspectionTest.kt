// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.swing.JButton

class AccessibleNameNotEmptyForFocusableComponentsInspectionTest {

  @Test
  fun `stateSet is full and name is not null or empty`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean {
        return true // imitate a real showing button
      }
    }
    button.isEnabled = true
    button.isFocusable = true

    val context = button.accessibleContext
    context.accessibleName = "name"

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(context)
    Assertions.assertTrue(result)
  }

  @Test
  fun `stateSet is full and name is empty`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean {
        return true // imitate a real showing button
      }
    }
    button.isEnabled = true
    button.isFocusable = true

    val context = button.accessibleContext
    context.accessibleName = ""

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(context)
    Assertions.assertFalse(result)
  }

  @Test
  fun `no focusable in stateSet`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean {
        return true // imitate a real showing button
      }
    }
    button.isEnabled = true
    button.isFocusable = false

    val context = button.accessibleContext
    context.accessibleName = ""

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(context)
    Assertions.assertTrue(result)
  }

  @Test
  fun `stateSet is full and name is null`() {
    val button = object : JButton() {
      override fun isShowing(): Boolean {
        return true // imitate a real showing button
      }
    }
    button.isEnabled = true
    button.isFocusable = true

    val context = button.accessibleContext
    context.accessibleName = null

    val result = AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(context)
    Assertions.assertFalse(result)
  }
}
