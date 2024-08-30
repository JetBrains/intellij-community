// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.*
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JSlider

class AccessibleEditableTextNotNullInspectionTest {

  @Test
  fun `valid role, valid stateSet and editableText not null`() {
    val text = JPasswordField()
    println(text.accessibleContext.accessibleStateSet)
    val result = AccessibleEditableTextNotNullInspection().passesInspection(text.accessibleContext)
    Assertions.assertTrue(result)
  }

  @Test
  fun `valid role, valid stateSet and editableText null`() {
    val component = object : JComponent() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleRole(): AccessibleRole {
            return AccessibleRole.PASSWORD_TEXT
          }
          override fun getAccessibleEditableText(): AccessibleEditableText? {
            return null
          }
          override fun getAccessibleStateSet(): AccessibleStateSet {
            return super.getAccessibleStateSet().also { it.add(AccessibleState.EDITABLE) }
          }
        }
      }
    }

    val result = AccessibleEditableTextNotNullInspection().passesInspection(component.accessibleContext)
    Assertions.assertFalse(result)
  }

  @Test
  fun `invalid stateSet and invalid role`() {
    val button = JButton()
    val result = AccessibleEditableTextNotNullInspection().passesInspection(button.accessibleContext)
    Assertions.assertTrue(result)
  }

  @Test
  fun `invalid role, valid stateSet`() {
    val slider = object : JSlider() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleRole(): AccessibleRole {
            return AccessibleRole.SLIDER
          }

          override fun getAccessibleStateSet(): AccessibleStateSet {
            return super.getAccessibleStateSet().also { it.add(AccessibleState.EDITABLE) }
          }
        }
      }
    }

    println(slider.accessibleContext.accessibleRole) // slider
    println(slider.accessibleContext.accessibleStateSet) // enabled,focusable,visible,horizontal
    println(slider.accessibleContext.accessibleEditableText) // null
    val result = AccessibleEditableTextNotNullInspection().passesInspection(slider.accessibleContext)
    Assertions.assertTrue(result)
  }


}