// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.plugin.config

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.UI.PanelFactory.grid
import com.intellij.util.ui.UI.PanelFactory.panel
import com.intellij.util.ui.UIUtil
import com.jetbrains.changeReminder.plugin.UserSettings
import java.awt.*
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.roundToInt

class ChangeReminderConfigurationPanel : Configurable {
  companion object {
    private const val LOWER_BOUND = 0
    private const val UPPER_BOUND = 100

    private fun reverseThreshold(value: Int) = (UPPER_BOUND - value + LOWER_BOUND)
  }

  private val userSettings = ServiceManager.getService(UserSettings::class.java)

  private var reversedValue = reverseThreshold((userSettings.threshold * UserSettings.THRESHOLD_PRECISION).roundToInt())

  private val thresholdField = JBIntSpinner(reversedValue, LOWER_BOUND, UPPER_BOUND).apply {
    addChangeListener(ThresholdFieldListener())
  }

  private val pluginActivateCheckBox = JBCheckBox("Enable additional Git repository index used by plugin (requires restart)",
                                                  userSettings.isPluginEnabled).apply {
    addChangeListener(ForwardIndexCheckboxListener())
  }

  private val thresholdSlider = JSlider(LOWER_BOUND, UPPER_BOUND, reversedValue).apply {
    addChangeListener(ThresholdSliderListener())
    val table = Hashtable<Int, JLabel>()
    table[LOWER_BOUND] = JLabel("Never show")
    table[UPPER_BOUND] = JLabel("Always show")
    labelTable = table
    paintLabels = true
    majorTickSpacing = UPPER_BOUND
    paintTicks = true
  }

  private val thresholdPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.X_AXIS)
    add(thresholdSlider)
    add(Box.createRigidArea(JBDimension(UIUtil.DEFAULT_HGAP, 0)))
    add(panel(thresholdField).resizeY(false).createPanel())
    border = IdeBorderFactory.createTitledBorder("How often to show reminders", false)
    alignmentX = Component.LEFT_ALIGNMENT
  }

  private val centerPanel = grid()
    .add(panel(pluginActivateCheckBox.apply { alignmentX = Component.LEFT_ALIGNMENT }))
    .add(panel(thresholdPanel).resizeX(false))
    .createPanel()

  override fun getDisplayName() = "ChangeReminder"

  override fun createComponent() = centerPanel

  override fun isModified(): Boolean {
    return reversedValue != thresholdSlider.value ||
           pluginActivateCheckBox.isSelected != userSettings.isPluginEnabled
  }

  override fun apply() {
    val isPluginEnabled = pluginActivateCheckBox.isSelected
    userSettings.isPluginEnabled = isPluginEnabled
    if (isPluginEnabled) {
      val newValue = thresholdField.value as Int
      userSettings.threshold = reverseThreshold(newValue).toDouble() / UserSettings.THRESHOLD_PRECISION
      reversedValue = newValue
    }
  }

  override fun reset() {
    thresholdSlider.value = reversedValue
    thresholdField.value = reversedValue
    pluginActivateCheckBox.isSelected = userSettings.isPluginEnabled
  }

  private inner class ThresholdSliderListener : ChangeListener {
    override fun stateChanged(e: ChangeEvent?) {
      val newValue = thresholdSlider.value
      if (thresholdField.value as Int != newValue) {
        thresholdField.value = newValue
      }
    }
  }

  private inner class ThresholdFieldListener : ChangeListener {
    override fun stateChanged(e: ChangeEvent?) {
      val newValue = thresholdField.value as Int
      if (thresholdSlider.value != newValue)
        thresholdSlider.value = newValue
    }
  }

  private inner class ForwardIndexCheckboxListener : ChangeListener {
    override fun stateChanged(e: ChangeEvent?) {
      val newValue = pluginActivateCheckBox.isSelected
      thresholdPanel.isEnabled = newValue
      thresholdField.isEnabled = newValue
      thresholdSlider.isEnabled = newValue
    }

  }
}