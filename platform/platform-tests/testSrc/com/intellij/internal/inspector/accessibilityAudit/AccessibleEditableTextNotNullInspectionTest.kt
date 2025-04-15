// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleEditableText
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState
import javax.accessibility.AccessibleStateSet
import javax.swing.JButton
import javax.swing.JPasswordField
import javax.swing.JSlider

class AccessibleEditableTextNotNullInspectionTest {

  @Test
  fun `valid role, valid stateSet and editableText not null`() {
    val text = JPasswordField()
    println(text.accessibleContext.accessibleStateSet)
    val result = AccessibleEditableTextNotNullInspection().passesInspection(text)
    Assertions.assertTrue(result)
  }

  @Test
  fun `valid role, valid stateSet and editableText null`() {
    val component = object : JButton() {
      override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
          accessibleContext = object : AccessibleJComponent() {
            override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PASSWORD_TEXT

            override fun getAccessibleEditableText(): AccessibleEditableText? = null

            override fun getAccessibleStateSet(): AccessibleStateSet = super.getAccessibleStateSet().also {
              it.add(AccessibleState.EDITABLE)
            }
          }
        }
        return accessibleContext
      }
    }

    val result = AccessibleEditableTextNotNullInspection().passesInspection(component)
    Assertions.assertFalse(result)
  }

  @Test
  fun `invalid stateSet and invalid role`() {
    val button = JButton()
    val result = AccessibleEditableTextNotNullInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }

  @Test
  fun `invalid role, valid stateSet`() {
    val slider = object : JSlider() {

      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleStateSet(): AccessibleStateSet = super.getAccessibleStateSet().also { it.add(AccessibleState.EDITABLE) }
        }
      }
    }
    val result = AccessibleEditableTextNotNullInspection().passesInspection(slider)
    Assertions.assertTrue(result)
  }
}