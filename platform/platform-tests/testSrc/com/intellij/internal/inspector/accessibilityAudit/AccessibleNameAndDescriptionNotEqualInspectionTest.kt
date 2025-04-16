// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.swing.JButton

class AccessibleNameAndDescriptionNotEqualInspectionTest {
  @Test
  fun `name and description not equal`() {
    val button = JButton()
    val context = button.accessibleContext
    context.accessibleName = "name"
    context.accessibleDescription = "description"

    val result = AccessibleNameAndDescriptionNotEqualInspection().passesInspection(context)
    Assertions.assertTrue(result)
  }

  @Test
  fun `name and description are equal`() {
    val button = JButton()
    val context = button.accessibleContext
    context.accessibleName = "same"
    context.accessibleDescription = "same"

    val result = AccessibleNameAndDescriptionNotEqualInspection().passesInspection(context)
    Assertions.assertFalse(result)
  }

  @Test
  fun `name and description are empty`() {
    val button = JButton()
    val context = button.accessibleContext
    context.accessibleName = ""
    context.accessibleDescription = ""

    val result = AccessibleNameAndDescriptionNotEqualInspection().passesInspection(context)
    Assertions.assertTrue(result)
  }
}
