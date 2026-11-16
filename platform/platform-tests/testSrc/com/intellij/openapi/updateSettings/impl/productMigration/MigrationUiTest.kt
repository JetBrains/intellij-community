// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.productMigration

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.productMigration.plainTextArea
import com.intellij.openapi.application.productMigration.plainTextButton
import com.intellij.openapi.application.productMigration.plainTextLink
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

  @Test
  fun `notification action text is plain text`() {
    val generic = IdeBundle.message("update.toolbar.widget.restart.text")
    assertEquals(generic, migrationReadyActionText(null))
    assertEquals(generic, migrationReadyActionText(MigrationReadyTexts("Ready", "<html><b>Restart</b>")))
    assertEquals(generic, migrationReadyActionText(MigrationReadyTexts("Ready", "<HTML>Restart")))
    assertEquals("Restart to Update", migrationReadyActionText(MigrationReadyTexts("Ready", "Restart to Update")))
    assertEquals("Save && Restart __now", migrationReadyActionText(MigrationReadyTexts("Ready", "Save & Restart _now")))
  }
}
