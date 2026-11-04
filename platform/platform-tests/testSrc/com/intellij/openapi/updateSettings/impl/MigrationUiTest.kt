// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.util.ui.JBFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.accessibility.AccessibleRole
import javax.swing.plaf.basic.BasicHTML

class MigrationUiTest {
  @Test
  fun `texts are shown as plain text`() {
    val source = "<html><b>A</b> < B & C"

    val area = plainTextArea(source, JBFont.label(), width = 300)
    assertEquals(source, area.text)
    assertEquals(AccessibleRole.LABEL, area.accessibleContext.accessibleRole)

    val button = plainTextButton(source) {}
    assertEquals(source, button.text)
    assertNull(button.getClientProperty(BasicHTML.propertyKey))

    val link = plainTextLink(source) {}
    assertEquals(source, link.text)
    assertNull(link.getClientProperty(BasicHTML.propertyKey))
  }
}
