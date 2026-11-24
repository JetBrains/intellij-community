// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.scale.JBUIScale
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that the setters of [UISettings] store their values in the keys of [UISettingsKeys], and that the getters read
 * them back.
 */
@TestApplication
class UISettingsFacadeTest {
  private val settings: UISettings
    get() = UISettings.getInstance()

  @BeforeEach
  @AfterEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    dropStoredUISettings()
  }

  @Test
  fun `the antialiasing setters store the keys and the getters read them`(): Unit = timeoutRunBlocking(60.seconds) {
    settings.ideAAType = AntialiasingType.OFF
    settings.editorAAType = AntialiasingType.OFF

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.IDE_ANTIALIASING, AntialiasingType.OFF)
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.EDITOR_ANTIALIASING, AntialiasingType.OFF)
    assertEquals(AntialiasingType.OFF, settings.ideAAType)
    assertEquals(AntialiasingType.OFF, settings.editorAAType)
  }

  @Test
  fun `the custom font setters store the keys and the getters read them`(): Unit = timeoutRunBlocking(60.seconds) {
    settings.overrideLafFonts = true
    settings.fontFace = "Dialog"
    settings.fontSize2D = 15.25f

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.CUSTOM_FONT_ENABLED, true)
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.CUSTOM_FONT_FACE, "Dialog")
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.CUSTOM_FONT_SIZE, 15.25f)
    assertEquals(true, settings.overrideLafFonts)
    assertEquals("Dialog", settings.fontFace)
    assertEquals(15.25f, settings.fontSize2D)
    assertEquals(15, settings.fontSize)

    settings.fontSize = 17

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.CUSTOM_FONT_SIZE, 17f)
  }

  @Test
  fun `a custom font size of zero or less reads as the default font size`(): Unit = timeoutRunBlocking(60.seconds) {
    assertEquals(UISettings.getDefFontSize(), settings.fontSize2D, "the default of the key")

    settings.fontSize2D = -3f

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.CUSTOM_FONT_SIZE, -3f)
    assertEquals(UISettings.getDefFontSize(), settings.fontSize2D)
  }

  @Test
  fun `no custom font face reads as the system font`() {
    assertEquals(JBUIScale.getSystemFontDataIfInitialized()?.first, settings.fontFace)
  }

  @Test
  fun `an instance over a given component reads the component and not the keys`(): Unit = timeoutRunBlocking(60.seconds) {
    settings.ideAAType = AntialiasingType.OFF
    settings.fontSize2D = 21f
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.IDE_ANTIALIASING, AntialiasingType.OFF)
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.CUSTOM_FONT_SIZE, 21f)
    val component = NotRoamableUiSettings()

    val instance = UISettings(component)
    assertEquals(component.ideAAType, instance.ideAAType)
    assertEquals(component.fontSize, instance.fontSize2D)
  }
}
