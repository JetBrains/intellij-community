// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.swing.JButton
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
class ExtendableTextFieldTest {
  @Test
  @RegistryKey("text.field.extension.buttons.focusable", "true")
  fun `focusable extension creates a focusable button component inside the text field`() {
    val textField = ExtendableTextField()
    val tooltip = "Add"
    val extension = ExtendableTextComponent.Extension.create(
      AllIcons.General.Add, AllIcons.General.Add, tooltip, true
    ) {}

    textField.addExtension(extension)

    val buttons = textField.components.filterIsInstance<JButton>()
    assertTrue(buttons.isNotEmpty())
    assertTrue(buttons.all { it.isFocusable })
    assertEquals(tooltip, buttons.first().accessibleContext.accessibleName)
  }

  @Test
  @RegistryKey("text.field.extension.buttons.focusable", "true")
  fun `non-focusable extensions stay as icons`() {
    val textField = ExtendableTextField()
    val extension = ExtendableTextComponent.Extension.create(
      AllIcons.General.Add, AllIcons.General.Add, "Add", false
    ) {}

    textField.addExtension(extension)

    val buttons = textField.components.filterIsInstance<JButton>()
    assertTrue(buttons.isEmpty())
  }
}

class ExtendableTextFieldTestWithoutApplication {
  @Test
  fun `adding extension doesn't throw without application`() {
    assertDoesNotThrow {
      val textField = ExtendableTextField()
      val extension = ExtendableTextComponent.Extension.create(AllIcons.General.Add, AllIcons.General.Add, "Add", true) {}
      textField.addExtension(extension)
    }
  }
}