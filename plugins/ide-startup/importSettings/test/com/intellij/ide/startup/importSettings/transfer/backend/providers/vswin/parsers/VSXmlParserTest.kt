// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.parsers

import com.intellij.testFramework.junit5.TestApplication
import org.jdom.Element
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import kotlin.io.path.bufferedWriter

@TestApplication
class VSXmlParserTest {
  @TempDir
  lateinit var tempDir: Path

  @ParameterizedTest
  @MethodSource("validBracePairColorizationValues")
  fun `brackets colorization goes to settings for a valid value`(value: String, expected: Boolean) {
    val settingsFile = writeSettingsFile(textEditorGeneral(bracePairColorization(value)))
    val settings = VSXmlParser(settingsFile).toSettings()

    assertEquals(expected, settings.bracketColorizationEnabled)
  }

  @ParameterizedTest
  @ValueSource(strings = ["1", "yes", ""])
  fun `brackets colorization stays null when the value is not boolean`(value: String) {
    val settingsFile = writeSettingsFile(textEditorGeneral(bracePairColorization(value)))
    val settings = VSXmlParser(settingsFile).toSettings()

    assertNull(settings.bracketColorizationEnabled)
  }

  @Test
  fun `brackets colorization goes to settings among other text editor properties`() {
    val settingsFile = writeSettingsFile(textEditorGeneral(
      property("EditorEmulation", "0"),
      bracePairColorization("false"),
      property("DragNDropTextEditing", "true"),
    ))
    val settings = VSXmlParser(settingsFile).toSettings()

    assertEquals(false, settings.bracketColorizationEnabled)
  }

  @Test
  fun `brackets colorization stays null when the option is absent`() {
    val settingsFile = writeSettingsFile(textEditorGeneral(property("DragNDropTextEditing", "true")))
    val settings = VSXmlParser(settingsFile).toSettings()

    assertNull(settings.bracketColorizationEnabled)
  }

  @Test
  fun `brackets colorization stays null when the text editor category is absent`() {
    val settingsFile = writeSettingsFile()
    val settings = VSXmlParser(settingsFile).toSettings()

    assertNull(settings.bracketColorizationEnabled)
  }

  /**
   * Writes the file with the raw format, so the parser gets the text values without trimming.
   */
  private fun writeSettingsFile(vararg categories: Element): Path {
    val root = Element("UserSettings")
      .addContent(Element("ApplicationIdentity").setAttribute("version", "17.0"))
      .addContent(Element("ToolsOptions"))
      .addContent(category("Environment_Group"))
    categories.forEach { root.addContent(it) }

    val file = tempDir.resolve("CurrentSettings.vssettings")
    file.bufferedWriter().use { XMLOutputter(Format.getRawFormat()).output(root, it) }
    return file
  }

  companion object {
    @JvmStatic
    fun validBracePairColorizationValues(): List<Arguments> = listOf(
      Arguments.of("true", true),
      Arguments.of("false", false),
      Arguments.of("True", true),
      Arguments.of("FALSE", false),
      Arguments.of("  true\n", true),
    )

    private fun textEditorGeneral(vararg properties: Element): Element = category("Text Editor_General", *properties)

    private fun bracePairColorization(value: String): Element = property("EnableBracePairColorizationOption", value)

    private fun category(name: String, vararg properties: Element): Element {
      val category = Element("Category").setAttribute("name", name).setAttribute("RegisteredName", name)
      properties.forEach { category.addContent(it) }
      return category
    }

    private fun property(name: String, value: String): Element = Element("PropertyValue").setAttribute("name", name).setText(value)
  }
}
