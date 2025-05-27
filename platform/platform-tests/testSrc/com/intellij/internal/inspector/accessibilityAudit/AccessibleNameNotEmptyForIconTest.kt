// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JButton

class AccessibleNameNotEmptyForIconTest {

  @Test
  fun `valid role and name not empty`() {
    val component = object : JButton() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleName(): String = "component"

          override fun getAccessibleRole(): AccessibleRole = AccessibleRole.ICON
        }
      }
    }
    val result = AccessibleNameNotEmptyForIcon().passesInspection(component)
    Assertions.assertTrue(result)
  }

  @Test
  fun `valid role and empty name`() {
    val component = object : JButton() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleName(): String  = ""

          override fun getAccessibleRole(): AccessibleRole = AccessibleRole.ICON
        }
      }
    }
    val result = AccessibleNameNotEmptyForIcon().passesInspection(component)
    Assertions.assertFalse(result)
  }

  @Test
  fun `invalid role`() {
    val button = JButton()
    val result = AccessibleNameNotEmptyForIcon().passesInspection(button)
    Assertions.assertTrue(result)
  }
}