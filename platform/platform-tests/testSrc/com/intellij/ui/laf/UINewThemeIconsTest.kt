// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.FILL_STROKE_SEPARATOR
import com.intellij.ide.ui.PaletteKeys
import com.intellij.ui.svg.*
import com.intellij.util.xml.dom.createXmlStreamReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class UINewThemeIconsTest {

  private val iconsPath = "/themes/expUI/icons/dark/"

  private val allAvailableKeys: Map<Set<String>, Set<String>> = mapOf(
    setOf("checkBox.svg", "radio.svg") to
      setOf("Checkbox.Background.Default", "Checkbox.Border.Default"),
    setOf("checkBoxSelected.svg", "checkBoxIndeterminateSelected.svg", "radioSelected.svg") to
      setOf("Checkbox.Foreground.Selected", "Checkbox.Background.Selected", "Checkbox.Border.Selected"),
    setOf("checkBoxFocused.svg", "radioFocused.svg") to
      setOf("Checkbox.Background.Default", "Checkbox.Border.Selected"),
    setOf("checkBoxSelectedFocused.svg", "checkBoxIndeterminateSelectedFocused.svg", "radioSelectedFocused.svg") to
      setOf("Checkbox.Foreground.Selected", "Checkbox.Background.Selected", "Checkbox.Border.Selected", "Checkbox.Focus.Wide"),
    setOf("checkBoxDisabled.svg", "radioDisabled.svg") to
      setOf("Checkbox.Background.Disabled", "Checkbox.Border.Disabled"),
    setOf("checkBoxSelectedDisabled.svg", "checkBoxIndeterminateSelectedDisabled.svg", "radioSelectedDisabled.svg") to
      setOf("Checkbox.Background.Disabled", "Checkbox.Border.Disabled", "Checkbox.Foreground.Disabled")
  )

  @Test
  fun testIcons() {
    for ((names, availableKeys) in allAvailableKeys.entries) {
      for (name in names) {
        checkIcon(name, availableKeys)
      }
    }
  }

  private fun checkIcon(name: String, availableKeys: Set<String>) {
    fun assertTrue(actual: Boolean, id: String, message: String) {
      if (!actual) {
        fail("Icon: $name, id: $id, $message")
      }
    }

    createJSvgDocument(createXmlStreamReader(javaClass.getResourceAsStream(iconsPath + name)!!), object : AttributeMutator {
      override fun invoke(attributes: MutableMap<String, String>) {
        val id = attributes[ATTR_ID] ?: fail("Icon: $name, $ATTR_ID not found")
        val separatorCount = id.count { it == FILL_STROKE_SEPARATOR }
        assertTrue(separatorCount < 2, id, "too many '$FILL_STROKE_SEPARATOR'")

        val paletteKeys = PaletteKeys(id)

        assertTrue(availableKeys.contains(paletteKeys.fillKey), id, "unknown key ${paletteKeys.fillKey}")
        assertTrue(availableKeys.contains(paletteKeys.strokeKey), id, "unknown key ${paletteKeys.strokeKey}")

        val colorAttributesCount = attributes.keys.count { it == ATTR_FILL || it == ATTR_STROKE }
        assertTrue(separatorCount + 1 == colorAttributesCount, id,
                   "found $colorAttributesCount attribute(s) which doesn't correspondent to id")
      }
    })
  }
}