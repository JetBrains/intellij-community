// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.plugin.config

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.layout.panel
import com.jetbrains.changeReminder.plugin.UserSettings
import java.util.*
import javax.swing.JLabel
import javax.swing.JSlider
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.roundToInt

class ChangeReminderConfigurationPanel : Configurable {
  companion object {
    private const val LOWER_BOUND = 0
    private const val UPPER_BOUND = 100

    private fun isFieldCorrect(field: JBTextField?): Boolean {
      if (field == null) {
        return false
      }
      val value = field.text.toIntOrNull() ?: return false
      return value in LOWER_BOUND..UPPER_BOUND
    }

    private fun reverseThreshold(value: Int) = (UPPER_BOUND - value + LOWER_BOUND)
  }

  private val userSettings = ServiceManager.getService(UserSettings::class.java)
  private var reversedValue = reverseThreshold(
    (userSettings.threshold * UserSettings.THRESHOLD_PRECISION).roundToInt())

  private val thresholdField by lazy {
    JBTextField(reversedValue.toString()).apply {
      document.addDocumentListener(FieldListener())
    }
  }

  private val pluginActivateCheckBox by lazy {
    JBCheckBox("Enable plugin (requires restart)", userSettings.isPluginEnabled)
  }

  private val thresholdSlider by lazy {
    JSlider(LOWER_BOUND, UPPER_BOUND, reversedValue).apply {
      addChangeListener(SliderListener())
      val table = Hashtable<Int, JLabel>()
      table[LOWER_BOUND] = JLabel("Never show")
      table[UPPER_BOUND] = JLabel("Always show")
      labelTable = table
      paintLabels = true
      majorTickSpacing = UPPER_BOUND
      paintTicks = true
    }
  }

  override fun getDisplayName() = "ChangeReminder"

  private fun getThresholdPanel() = panel {
    row("Adjust triggering level: ") {
      thresholdSlider(grow, push)
      thresholdField(push)
    }
  }

  private fun createCenterPanel() = panel {
    row {
      cell(true) {
        getThresholdPanel()()
        pluginActivateCheckBox()
      }
    }
  }

  override fun createComponent() = createCenterPanel()

  override fun isModified(): Boolean {
    if (!isFieldCorrect(thresholdField)) {
      return false
    }
    return reversedValue != thresholdSlider.value ||
           pluginActivateCheckBox.isSelected != userSettings.isPluginEnabled
  }

  override fun apply() {
    if (isFieldCorrect(thresholdField)) {
      val newValue = thresholdSlider.value
      userSettings.threshold = reverseThreshold(newValue).toDouble() / UserSettings.THRESHOLD_PRECISION
      reversedValue = newValue
      userSettings.isPluginEnabled = pluginActivateCheckBox.isSelected
    }
  }

  override fun reset() {
    thresholdSlider.value = reversedValue
    thresholdField.text = reversedValue.toString()
    pluginActivateCheckBox.isSelected = userSettings.isPluginEnabled
  }

  private inner class SliderListener : ChangeListener {
    override fun stateChanged(e: ChangeEvent?) {
      val newValue = thresholdSlider.value
      if (thresholdField.text.toInt() != newValue) {
        thresholdField.text = newValue.toString()
      }
    }
  }

  private inner class FieldListener : DocumentListener {
    private fun sliderUpdate() {
      if (isFieldCorrect(thresholdField)) {
        thresholdSlider.value = thresholdField.text.toInt()
      }
    }

    override fun changedUpdate(e: DocumentEvent?) {
    }

    override fun insertUpdate(e: DocumentEvent?) {
      sliderUpdate()
    }

    override fun removeUpdate(e: DocumentEvent?) {
      sliderUpdate()
    }
  }
}