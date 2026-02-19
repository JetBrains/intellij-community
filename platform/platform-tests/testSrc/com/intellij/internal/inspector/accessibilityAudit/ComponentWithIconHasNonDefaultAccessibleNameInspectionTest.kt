// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleColoredComponent
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import javax.accessibility.AccessibleContext
import javax.swing.JLabel

class ComponentWithIconHasNonDefaultAccessibleNameInspectionTest {

  @Test
  fun `label with icon and default accessible name`() {
    val label = JLabel("Test Label")
    label.icon = AllIcons.Empty

    val result = ComponentWithIconHasNonDefaultAccessibleNameInspection().passesInspection(label)
    Assertions.assertFalse(result)
  }

  @Test
  fun `label with icon and custom accessible name`() {
    val label = JLabel("Test Label")
    label.icon = AllIcons.Empty
    label.accessibleContext.accessibleName = "Important Test Label"

    val result = ComponentWithIconHasNonDefaultAccessibleNameInspection().passesInspection(label)
    Assertions.assertTrue(result)
  }

  @Test
  fun `label without icon`() {
    val label = JLabel("Test Label")

    val result = ComponentWithIconHasNonDefaultAccessibleNameInspection().passesInspection(label)
    Assertions.assertTrue(result)
  }

  @Test
  fun `label with empty text`() {
    val label = JLabel("")
    label.icon = AllIcons.Empty

    val result = ComponentWithIconHasNonDefaultAccessibleNameInspection().passesInspection(label)
    Assertions.assertTrue(result)
  }

  @Test
  fun `label with null accessible name`() {
    val label = object : JLabel("Test Label") {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleJLabel() {
          override fun getAccessibleName(): String? = null
        }
      }
    }
    label.icon = AllIcons.Empty

    val result = ComponentWithIconHasNonDefaultAccessibleNameInspection().passesInspection(label)
    Assertions.assertTrue(result)
  }

  @Test
  fun `simple colored component with icon and default accessible name`() {
    val component = SimpleColoredComponent()
    component.icon = AllIcons.Empty
    component.append("Test Component")

    val result = ComponentWithIconHasNonDefaultAccessibleNameInspection().passesInspection(component)
    Assertions.assertFalse(result)
  }

  @Test
  fun `simple colored component with icon and custom accessible name`() {
    val component = object : SimpleColoredComponent() {
      override fun getAccessibleContext(): AccessibleContext {
        return object : AccessibleSimpleColoredComponent() {
          override fun getAccessibleName(): String = "Important Test Component"
        }
      }
    }

    component.icon = AllIcons.Empty
    component.append("Test Component")

    val result = ComponentWithIconHasNonDefaultAccessibleNameInspection().passesInspection(component)
    Assertions.assertTrue(result)
  }
}
