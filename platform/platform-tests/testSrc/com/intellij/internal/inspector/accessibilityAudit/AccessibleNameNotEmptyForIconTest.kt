// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions

import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JButton
import javax.swing.JComponent

class AccessibleNameNotEmptyForIconTest {

  @Test
  fun `valid role and name not empty`() {
    val component = object : JComponent() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleName(): String {
            return "component"
          }

          override fun getAccessibleRole(): AccessibleRole {
            return AccessibleRole.ICON
          }
        }
      }
    }
    val result = AccessibleNameNotEmptyForIcon().passesInspection(component.accessibleContext)
    Assertions.assertTrue(result)
  }

  @Test
  fun `valid role and empty name`() {
    val component = object : JComponent() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleName(): String {
            return ""
          }

          override fun getAccessibleRole(): AccessibleRole {
            return AccessibleRole.ICON
          }
        }
      }
    }
    val result = AccessibleNameNotEmptyForIcon().passesInspection(component.accessibleContext)
    Assertions.assertFalse(result)
  }

  @Test
  fun `invalid role`() {
    val button = JButton()
    val result = AccessibleNameNotEmptyForIcon().passesInspection(button.accessibleContext)
    Assertions.assertTrue(result)
  }
}