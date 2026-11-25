// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that [NotRoamableUiSettingsImport] imports the state of [NotRoamableUiSettings] into the keys of
 * `UISettingsKeys`. A test sets the state through the setters of the component and runs the migration. The test
 * puts back the state that the component had before it.
 */
@TestApplication
class NotRoamableUiSettingsImportTest {
  private val component: NotRoamableUiSettings
    get() = NotRoamableUiSettings.getInstance()
  private lateinit var stateBeforeTest: NotRoamableUiOptions

  @BeforeEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    stateBeforeTest = component.state
    dropStoredUISettings()
  }

  @AfterEach
  fun restoreSettings(): Unit = timeoutRunBlocking(60.seconds) {
    component.setValues(stateBeforeTest)
    dropStoredUISettings()
  }

  @Test
  fun `the migration imports each value of the component into its key`(): Unit = timeoutRunBlocking(60.seconds) {
    component.apply {
      ideAAType = AntialiasingType.OFF
      editorAAType = AntialiasingType.OFF
      fontFace = "Dialog"
      fontSize = 21f
      overrideLafFonts = true
      ideScale = 1.5f
      presentationModeIdeScale = 2f
      experimentalSingleStripe = true
    }

    migrate()

    assertEquals(AntialiasingType.OFF, readKey(UISettingsKeyIdentities.IDE_ANTIALIASING))
    assertEquals(AntialiasingType.OFF, readKey(UISettingsKeyIdentities.EDITOR_ANTIALIASING))
    assertEquals("Dialog", readKey(UISettingsKeyIdentities.CUSTOM_FONT_FACE))
    assertEquals(21f, readKey(UISettingsKeyIdentities.CUSTOM_FONT_SIZE))
    assertEquals(true, readKey(UISettingsKeyIdentities.CUSTOM_FONT_ENABLED))
    assertEquals(1.5f, readKey(UISettingsKeyIdentities.IDE_SCALE))
    assertEquals(2f, readKey(UISettingsKeyIdentities.PRESENTATION_MODE_IDE_SCALE))
    assertEquals(true, readKey(UISettingsKeyIdentities.SINGLE_STRIPE))
  }

  @Test
  fun `the migration of the default values stores no value`(): Unit = timeoutRunBlocking(60.seconds) {
    val appContext = SettingsContext.app()
    component.apply {
      ideAAType = uiSettingsKey(UISettingsKeyIdentities.IDE_ANTIALIASING).default(appContext) as AntialiasingType
      editorAAType = uiSettingsKey(UISettingsKeyIdentities.EDITOR_ANTIALIASING).default(appContext) as AntialiasingType
      fontFace = null
      fontSize = 0f
      overrideLafFonts = false
      ideScale = 1f
      presentationModeIdeScale = 0f
      experimentalSingleStripe = false
    }

    migrate()

    for (identity in UISettingsKeyIdentities.ALL) {
      assertEquals(emptyList<Any?>(), uiSettingsKey(identity).values(appContext), "the stored values of '$identity'")
    }
  }

  @Test
  fun `a repeated migration keeps a value that the user changed after the import`(): Unit = timeoutRunBlocking(60.seconds) {
    component.apply {
      ideScale = 1.5f
      experimentalSingleStripe = true
    }
    migrate()
    writeUISettingsKey(UISettingsKeyIdentities.IDE_SCALE, 1.25f)
    writeUISettingsKey(UISettingsKeyIdentities.SINGLE_STRIPE, false)

    migrate()

    assertEquals(1.25f, readKey(UISettingsKeyIdentities.IDE_SCALE))
    assertEquals(false, readKey(UISettingsKeyIdentities.SINGLE_STRIPE))
  }

  /** Returns the value of the key of [identity] at the application context. */
  private suspend fun readKey(identity: String): Any? = uiSettingsKey(identity)[SettingsContext.app()]

  /** Runs the migration, as the platform does after the first start. */
  private suspend fun migrate() {
    NotRoamableUiSettingsImport().migrate(SettingsContext.app())
  }
}

/** Sets each value of [options] that has a key through the setters of the component. */
private fun NotRoamableUiSettings.setValues(options: NotRoamableUiOptions) {
  ideAAType = options.ideAAType
  editorAAType = options.editorAAType
  fontFace = options.fontFace
  fontSize = options.fontSize
  overrideLafFonts = options.overrideLafFonts
  ideScale = options.ideScale
  presentationModeIdeScale = options.presentationModeIdeScale
  experimentalSingleStripe = options.experimentalSingleStripe
}
