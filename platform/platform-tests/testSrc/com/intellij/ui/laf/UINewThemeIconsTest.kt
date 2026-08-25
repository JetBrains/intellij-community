// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.FILL_STROKE_SEPARATOR
import com.intellij.ide.ui.PaletteKeys
import com.intellij.ui.svg.ATTR_FILL
import com.intellij.ui.svg.ATTR_ID
import com.intellij.ui.svg.ATTR_STROKE
import com.intellij.ui.svg.AttributeMutator
import com.intellij.ui.svg.createJSvgDocument
import com.intellij.util.xml.dom.createXmlStreamReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class UINewThemeIconsTest {

  private val iconsPath = "/themes/expUI/icons/dark/"

  private val lafIconsPath = "/com/intellij/ide/ui/laf/icons/"

  /**
   * Islands toggles have no per-theme copies — a single asset per state, recolored via `Toggle.*` palette keys
   */
  private val toggleAvailableKeys: Map<Set<String>, Set<String>> = mapOf(
    setOf("toggleOff.svg") to
      setOf("Toggle.Background.Default", "Toggle.Border.Default", "Toggle.Foreground.Default"),
    setOf("toggleOn.svg") to
      setOf("Toggle.Background.Selected", "Toggle.Border.Selected", "Toggle.Foreground.Selected"),
    setOf("toggleOffDisabled.svg") to
      setOf("Toggle.Background.Disabled", "Toggle.Border.Disabled", "Toggle.Foreground.Disabled"),
    setOf("toggleOnDisabled.svg") to
      setOf("Toggle.Background.SelectedDisabled", "Toggle.Border.SelectedDisabled", "Toggle.Foreground.SelectedDisabled")
  )

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
        checkIcon(name, availableKeys, iconsPath)
      }
    }
  }

  @Test
  fun testToggleIcons() {
    for ((names, availableKeys) in toggleAvailableKeys.entries) {
      for (name in names) {
        checkIcon(name, availableKeys, lafIconsPath)
      }
    }
  }

  /**
   * The toggle assets are shared by all themes, so an Islands theme that misses a key renders that
   * element with the color baked into the asset instead of its own.
   */
  @Test
  fun testToggleKeysDeclaredByIslandsThemes() {
    val themes = listOf("ManyIslandsDark", "ManyIslandsLight", "ManyIslandsDarcula", "HighContrast")
    for (theme in themes) {
      val path = "/themes/islands/$theme.theme.json"
      val text = javaClass.getResourceAsStream(path)?.reader()?.readText() ?: fail("Theme not found: $path")
      for (key in toggleAvailableKeys.values.flatten()) {
        if (!text.contains("\"$key\"")) {
          fail("Theme: $theme, palette key $key is not declared, see IslandsOnOffButtonUI-spec.md")
        }
      }
    }
  }

  private fun checkIcon(name: String, availableKeys: Set<String>, path: String) {
    fun assertTrue(actual: Boolean, id: String, message: String) {
      if (!actual) {
        fail("Icon: $name, id: $id, $message")
      }
    }

    createJSvgDocument(createXmlStreamReader(javaClass.getResourceAsStream(path + name)!!), object : AttributeMutator {
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