/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.colorpicker

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.JBUI
import sun.security.util.SecurityConstants
import java.awt.*
import javax.swing.*
import javax.swing.plaf.basic.BasicButtonUI
import kotlin.math.abs

private val PANEL_PREFERRED_SIZE = JBUI.size(PICKER_PREFERRED_WIDTH, 80)
private val PANEL_BORDER = JBUI.Borders.empty(4, HORIZONTAL_MARGIN_TO_PICKER_BORDER, 0, HORIZONTAL_MARGIN_TO_PICKER_BORDER)

private val PIPETTE_BUTTON_BORDER = JBUI.Borders.empty()

private val COLOR_INDICATOR_SIZE = JBUI.size(45)
private val COLOR_INDICATOR_BORDER = JBUI.Borders.empty(6)

private val HUE_SLIDER_BORDER = JBUI.Borders.empty(0, 16, 8, 16)

private val ALPHA_SLIDER_BORDER = JBUI.Borders.empty(8, 16, 0, 16)

class ColorAdjustPanel(private val model: ColorPickerModel,
                       private val pipetteProvider: ColorPipetteProvider)
  : JPanel(GridBagLayout()), ColorListener {

  private val pipetteButton by lazy {
    val colorPipetteButton = ColorPipetteButton(model, pipetteProvider.createPipette(this@ColorAdjustPanel))
    with (colorPipetteButton) {
      border = PIPETTE_BUTTON_BORDER
      background = PICKER_BACKGROUND_COLOR

      ui = BasicButtonUI()

      isFocusable = false
      preferredSize = COLOR_INDICATOR_SIZE
    }
    colorPipetteButton
  }

  @VisibleForTesting
  val colorIndicator = ColorIndicator().apply {
    border = COLOR_INDICATOR_BORDER
    preferredSize = COLOR_INDICATOR_SIZE
  }

  @VisibleForTesting
  val hueSlider = HueSliderComponent().apply {
    border = HUE_SLIDER_BORDER
    background = PICKER_BACKGROUND_COLOR

    addListener {
      val hue = it / 360f
      val hsbValues = Color.RGBtoHSB(model.color.red, model.color.green, model.color.blue, null)
      val rgb = Color.HSBtoRGB(hue, hsbValues[1], hsbValues[2])
      val argb = (model.color.alpha shl 24) or (rgb and 0x00FFFFFF)
      val newColor = Color(argb, true)
      model.setColor(newColor, this@ColorAdjustPanel)
    }
  }

  @VisibleForTesting
  val alphaSlider = AlphaSliderComponent().apply {
    border = ALPHA_SLIDER_BORDER
    background = PICKER_BACKGROUND_COLOR

    addListener {
      model.setColor(Color(model.color.red, model.color.green, model.color.blue, it), this@ColorAdjustPanel)
    }
  }

  init {
    border = PANEL_BORDER
    background = PICKER_BACKGROUND_COLOR
    preferredSize = PANEL_PREFERRED_SIZE

    // TODO: replace GridBag with other layout.
    val c = GridBagConstraints()

    if (canPickupColorFromDisplay()) {
      c.gridx = 0
      c.gridy = 0
      c.weightx = 0.12
      add(pipetteButton, c)
    }

    c.gridx = 1
    c.gridy = 0
    c.weightx = 0.12
    add(colorIndicator, c)

    c.fill = GridBagConstraints.BOTH
    c.gridx = 2
    c.gridy = 0
    c.weightx = 0.76
    val sliderPanel = JPanel()
    sliderPanel.layout = BoxLayout(sliderPanel, BoxLayout.Y_AXIS)
    sliderPanel.border = JBUI.Borders.empty()
    sliderPanel.background = PICKER_BACKGROUND_COLOR
    sliderPanel.add(hueSlider)
    sliderPanel.add(alphaSlider)
    add(sliderPanel, c)

    model.addListener(this)
  }

  override fun colorChanged(color: Color, source: Any?) {
    if (colorIndicator.color != color) {
      colorIndicator.color = color
    }

    val hue = Color.RGBtoHSB(color.red, color.green, color.blue, null)[0]
    val hueDegree = Math.round(hue * 360)
    // Don't change hueSlider.value when (hueSlider.value, hueDegree) is (0, 360) or (360, 0).
    if (abs(hueSlider.value - hueDegree) != 360) {
      hueSlider.value = hueDegree
    }

    alphaSlider.sliderBackgroundColor = color
    if (alphaSlider.value != color.alpha) {
      alphaSlider.value = color.alpha
    }

    repaint()
  }
}

private fun canPickupColorFromDisplay(): Boolean {
  val alphaModeSupported = WindowManager.getInstance()?.isAlphaModeSupported ?: false
  if (!alphaModeSupported) {
    return false
  }

  return try {
    System.getSecurityManager()?.checkPermission(SecurityConstants.AWT.READ_DISPLAY_PIXELS_PERMISSION)
    true
  }
  catch (e: SecurityException) {
    false
  }
}
