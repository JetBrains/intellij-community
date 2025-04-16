// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JCheckBox
import javax.swing.JProgressBar

class AccessibleActionNotNullInspectionTest {

  @Test
  fun `valid role and action not null`() {
    val box = JCheckBox()
    val result = AccessibleActionNotNullInspection().passesInspection(box.accessibleContext)
    Assertions.assertTrue(result)
  }

  @Test
  fun `invalid role and action not null`() {
    val bar = JProgressBar()
    val result = AccessibleActionNotNullInspection().passesInspection(bar.accessibleContext)
    Assertions.assertTrue(result)
  }

  @Test
  fun `valid role and action null`() {
    val box = object : JCheckBox() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJComponent() {
          override fun getAccessibleRole(): AccessibleRole {
            return AccessibleRole.CHECK_BOX
          }
          override fun getAccessibleAction(): AccessibleAction? {
            return null
          }

        }
      }
    }
    val result = AccessibleActionNotNullInspection().passesInspection(box.accessibleContext)
    Assertions.assertFalse(result)
  }
}