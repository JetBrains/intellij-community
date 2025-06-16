// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.swing.JComponent

class ImplementsAccessibleInterfaceInspectionTest {

  @Test
  fun `component that implements Accessible interface`() {
    val component = object : JComponent(), Accessible {
      override fun getAccessibleContext(): AccessibleContext = super.getAccessibleContext()
    }

    val result = ImplementsAccessibleInterfaceInspection().passesInspection(component)
    Assertions.assertTrue(result)
  }

  @Test
  fun `component that does not implement Accessible interface`() {
    val component = object : JComponent() {} // Doesn't implement Accessible

    @Suppress("CAST_NEVER_SUCCEEDS")
    val result = ImplementsAccessibleInterfaceInspection().passesInspection(component as? Accessible)
    Assertions.assertFalse(result)
  }

  @Test
  fun `null component`() {
    val result = ImplementsAccessibleInterfaceInspection().passesInspection(null)
    Assertions.assertFalse(result)
  }
}