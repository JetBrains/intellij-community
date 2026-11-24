// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.TestActionEvent
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
import javax.swing.JComponent
import javax.swing.UIManager
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that the zoom actions and the scale input of the Accessibility page store [UISettings.ideScale] in its key, and
 * that the input follows the scale. The page is the `preferences.accessibility` configurable.
 */
@TestApplication
class IdeScaleSettingsTest {
  private val configurable: Configurable by lazy {
    Configurable.APPLICATION_CONFIGURABLE.extensionList.single { it.id == "preferences.accessibility" }.createConfigurable()!!
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
  fun `the zoom in action stores the increased scale in the key`(): Unit = timeoutRunBlocking(60.seconds) {
    withContext(Dispatchers.EDT) {
      val action = ActionManager.getInstance().getAction("ZoomInIdeAction")
      ActionUtil.performAction(action, TestActionEvent.createTestEvent(action))
    }

    waitUntil("the key holds a greater scale") {
      (storedUISettingsValues(UISettingsKeyIdentities.IDE_SCALE).singleOrNull() as Float? ?: 0f) > 1f
    }
  }

  @Test
  fun `the scale input stores its scale in the key`(): Unit = timeoutRunBlocking(60.seconds) {
    withContext(Dispatchers.EDT) {
      configurable.createComponent()!!.scaleInput().selectedItem = 1.5f.percentStringValue
    }

    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.IDE_SCALE, 1.5f)
  }

  @Test
  fun `a scale written through the setter appears in the open page`(): Unit = timeoutRunBlocking(60.seconds) {
    val scaleInput = withContext(Dispatchers.EDT) { configurable.createComponent()!!.scaleInput() }

    UISettings.getInstance().ideScale = 1.25f

    waitUntil("the input shows the written scale") { withContext(Dispatchers.EDT) { scaleInput.item == 1.25f.percentStringValue } }
  }

  @Test
  fun `a density change keeps the fonts at the IDE scale`(): Unit = timeoutRunBlocking(60.seconds) {
    // The look and feel manager of the test application is headless, so the test takes the real one.
    val lafManager = withContext(Dispatchers.EDT) {
      LafManagerImpl.getTestInstance()!!.also { it.setCurrentLookAndFeel(it.defaultLightLaf, true) }
    }
    UISettings.getInstance().ideScale = 1.25f
    waitUntilUISettingsKeyStores(UISettingsKeyIdentities.IDE_SCALE, 1.25f)
    try {
      val fontSizeAtScale = withContext(Dispatchers.EDT) {
        lafManager.updateUI()
        UIManager.getFont("Label.font").size2D
      }

      val fontSizeAfterDensityChange = withContext(Dispatchers.EDT) {
        lafManager.applyDensity()
        UIManager.getFont("Label.font").size2D
      }

      assertEquals(fontSizeAtScale, fontSizeAfterDensityChange)
    }
    finally {
      UISettings.getInstance().ideScale = 1f
      waitUntilUISettingsKeyStores(UISettingsKeyIdentities.IDE_SCALE, 1f)
      withContext(Dispatchers.EDT) { lafManager.updateUI() }
    }
  }

  /** Returns the input of the IDE scale of this page. */
  private fun JComponent.scaleInput(): ComboBox<String> {
    @Suppress("UNCHECKED_CAST")
    return UIUtil.findComponentsOfType(this, ComboBox::class.java)
      .single { it.isEditable && (0 until it.model.size).any { index -> it.model.getElementAt(index) == 1f.percentStringValue } } as ComboBox<String>
  }
}
