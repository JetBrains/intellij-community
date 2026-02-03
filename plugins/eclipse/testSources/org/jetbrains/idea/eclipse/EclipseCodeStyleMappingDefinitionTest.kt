// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.idea.eclipse.codeStyleMapping.buildEclipseCodeStyleMappingTo
import org.jetbrains.idea.eclipse.codeStyleMapping.util.MappingDefinitionElement
import org.jetbrains.idea.eclipse.codeStyleMapping.util.UnexpectedIncomingValue
import org.jetbrains.idea.eclipse.codeStyleMapping.valueConversions.EclipseWrapValue
import org.jetbrains.idea.eclipse.importer.EclipseCodeStyleSchemeImporter.Companion.readEclipseXmlProfileOptions
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions
import org.jetbrains.idea.eclipse.importer.EclipseFormatterOptions.LineWrapPolicy
import kotlin.io.path.div

class EclipseCodeStyleMappingDefinitionTest : LightPlatformTestCase() {
  fun testEveryEclipseIdIsMapped() {
    val codeStyleSettings = CodeStyle.createTestSettings()
    val mapping = buildEclipseCodeStyleMappingTo(codeStyleSettings)
    val idsInMapping = mapping.elements
      .filterIsInstance<MappingDefinitionElement.IdToSettingMapping>()
      .map { it.id }
    val xmlFromEclipse = VfsUtil.findFile(eclipseTestDataRoot / "import" / "settings" / "eclipse_exported.xml", false)
    assertNotNull(xmlFromEclipse)
    val idsInExportFromEclipse = xmlFromEclipse!!.readEclipseXmlProfileOptions().keys

    assertEquals(idsInExportFromEclipse.size, idsInMapping.size)
    assertEquals(idsInMapping.toSet(), idsInExportFromEclipse)
  }

  fun testEclipseWrapValue() {
    var wrap = EclipseWrapValue.decode(16)
    assertEquals(LineWrapPolicy.WRAP_WHERE_NECESSARY, wrap.lineWrapPolicy)
    assertFalse(wrap.isForceSplit)
    wrap.indentationPolicy = EclipseFormatterOptions.IndentationPolicy.INDENT_ON_COLUMN
    assertEquals(18, wrap.encode())

    wrap = EclipseWrapValue.decode(83)
    assertEquals(LineWrapPolicy.WRAP_ALL_EXCEPT_FIRST, wrap.lineWrapPolicy)
    assertTrue(wrap.isForceSplit)
    assertTrue(wrap.isAligned)
  }

  fun testInvalidEclipseWrapValue() {
    assertThrows(UnexpectedIncomingValue::class.java) { EclipseWrapValue.decode(6) }
    assertThrows(UnexpectedIncomingValue::class.java) { EclipseWrapValue.decode(96) }
  }
}