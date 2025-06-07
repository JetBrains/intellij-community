// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleText
import javax.swing.JButton
import javax.swing.JPasswordField

class AccessibleTextNotNullInspectionTest {

  @Test
  fun `valid role and text not null`() {
    val text = JPasswordField()
    val result = AccessibleTextNotNullInspection().passesInspection(text)
    Assertions.assertTrue(result)
  }

  @Test
  fun `valid role and text null`() {
    val button = object : JPasswordField() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PASSWORD_TEXT
          override fun getAccessibleText(): AccessibleText? = null
        }
      }
    }
    val result = AccessibleTextNotNullInspection().passesInspection(button)
    Assertions.assertFalse(result)

  }

  @Test
  fun `invalid role`() {
    val button = JButton()
    val result = AccessibleTextNotNullInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }
}