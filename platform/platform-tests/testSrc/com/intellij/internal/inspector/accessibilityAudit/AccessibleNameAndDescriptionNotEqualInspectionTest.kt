// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.swing.JButton

class AccessibleNameAndDescriptionNotEqualInspectionTest {
  @Test
  fun `name and description not equal`() {
    val button = JButton()
    button.accessibleContext.accessibleName = "name"
    button.accessibleContext.accessibleDescription = "description"

    val result = AccessibleNameAndDescriptionNotEqualInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }

  @Test
  fun `name and description are equal`() {
    val button = JButton()
    button.accessibleContext.accessibleName = "same"
    button.accessibleContext.accessibleDescription = "same"

    val result = AccessibleNameAndDescriptionNotEqualInspection().passesInspection(button)
    Assertions.assertFalse(result)
  }

  @Test
  fun `name and description are empty`() {
    val button = JButton()
    button.accessibleContext.accessibleName = ""
    button.accessibleContext.accessibleDescription = ""

    val result = AccessibleNameAndDescriptionNotEqualInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }

  @Test
  fun `name and description are null`() {
    val button = JButton()
    button.accessibleContext.accessibleName = null
    button.accessibleContext.accessibleDescription = null

    val result = AccessibleNameAndDescriptionNotEqualInspection().passesInspection(button)
    Assertions.assertTrue(result)
  }
}
