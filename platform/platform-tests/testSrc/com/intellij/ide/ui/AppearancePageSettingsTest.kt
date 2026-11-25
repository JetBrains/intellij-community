// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox
import javax.swing.JComponent
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that an Apply of the Appearance page stores its inputs in the keys of [UISettingsKeys]. A test edits the inputs
 * as a user does, through the components of the page. The page is the `preferences.lookFeel` configurable.
 */
@TestApplication
class AppearancePageSettingsTest {
  private val configurable: Configurable by lazy {
    Configurable.APPLICATION_CONFIGURABLE.extensionList.single { it.id == "preferences.lookFeel" }.createConfigurable()!!
  }

  @BeforeEach
  fun dropStoredSettingsBefore(): Unit = timeoutRunBlocking(60.seconds) {
    dropStoredUISettings()
  }

  @AfterEach
  fun disposePageAndDropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    withContext(Dispatchers.EDT) { configurable.disposeUIResources() }
    dropStoredUISettings()
  }

  @Test
  fun `an Apply writes the changed antialiasing input to its key`(): Unit = timeoutRunBlocking(60.seconds) {
    val newIdeAntialiasing = withContext(Dispatchers.EDT) {
      val ideInput = configurable.createComponent()!!.antialiasingInputs().first()
      ideInput.otherItem().also {
        ideInput.selectedItem = it
        configurable.apply()
      }
    }

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.IDE_ANTIALIASING, newIdeAntialiasing)
    assertEquals(emptyList<AntialiasingType>(), storedUISettingsValues(UISettingsKeyIdentities.EDITOR_ANTIALIASING),
                 "an untouched input writes nothing")
  }

  @Test
  fun `an Apply that enables the custom font stores the flag`(): Unit = timeoutRunBlocking(60.seconds) {
    withContext(Dispatchers.EDT) {
      configurable.createComponent()!!.customFontCheckBox().isSelected = true
      configurable.apply()
    }

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.CUSTOM_FONT_ENABLED, true)
  }

  @Test
  fun `a presentation mode scale written through the setter appears in the open page`(): Unit = timeoutRunBlocking(60.seconds) {
    val scaleInput = withContext(Dispatchers.EDT) { configurable.createComponent()!!.presentationModeScaleInput() }

    UISettings.getInstance().presentationModeIdeScale = 2.5f

    waitUntil("the input shows the written scale") { withContext(Dispatchers.EDT) { scaleInput.item == 2.5f.percentStringValue } }
  }

  /** Returns the input of the IDE scale in the presentation mode. Only its options hold the scale `2.5`. */
  private fun JComponent.presentationModeScaleInput(): ComboBox<*> =
    UIUtil.findComponentsOfType(this, ComboBox::class.java)
      .single { it.isEditable && (0 until it.model.size).any { index -> it.model.getElementAt(index) == 2.5f.percentStringValue } }

  /** Returns the check box of this page that turns the custom font on. */
  private fun JComponent.customFontCheckBox(): JCheckBox =
    UIUtil.findComponentsOfType(this, JCheckBox::class.java).single { it.text == IdeBundle.message("checkbox.override.default.laf.fonts") }

  /** Returns the antialiasing inputs of this page: the input of the IDE, then the input of the editor. */
  private fun JComponent.antialiasingInputs(): List<ComboBox<*>> =
    UIUtil.findComponentsOfType(this, ComboBox::class.java).filter { it.model.size > 0 && it.model.getElementAt(0) is AntialiasingType }
      .also { assertEquals(2, it.size, "the antialiasing inputs") }

  /** Returns an item of this input that differs from the selected one. */
  private fun ComboBox<*>.otherItem(): Any = (0 until model.size).map { model.getElementAt(it) }.first { it != selectedItem }
}
