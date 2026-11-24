// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
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
  fun `an instance over a given component reads the component and not the keys`(): Unit = timeoutRunBlocking(60.seconds) {
    settings.ideAAType = AntialiasingType.OFF
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.IDE_ANTIALIASING, AntialiasingType.OFF)
    val component = NotRoamableUiSettings()

    assertEquals(component.ideAAType, UISettings(component).ideAAType)
  }
}
