// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

  /** Returns the antialiasing inputs of this page: the input of the IDE, then the input of the editor. */
  private fun JComponent.antialiasingInputs(): List<ComboBox<*>> =
    UIUtil.findComponentsOfType(this, ComboBox::class.java).filter { it.model.size > 0 && it.model.getElementAt(0) is AntialiasingType }
      .also { assertEquals(2, it.size, "the antialiasing inputs") }

  /** Returns an item of this input that differs from the selected one. */
  private fun ComboBox<*>.otherItem(): Any = (0 until model.size).map { model.getElementAt(it) }.first { it != selectedItem }
}
