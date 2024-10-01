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
@file:ApiStatus.Internal

package com.intellij.ui.colorpicker

import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.geom.Point2D
import kotlin.math.max
import kotlin.math.min

private val COLORS = arrayOf(Color.RED, Color.ORANGE, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
private val POINTS = COLORS.mapIndexed { index, color ->
  if (color == Color.RED && index != 0) {
    1.0f
  }
  else {
    Color.RGBtoHSB(color.red, color.green, color.blue, null)[0]
  }
}.toFloatArray()

const val SLIDE_UNIT: Int = 1

@ApiStatus.Internal
class HueSliderComponent : SliderComponent<Int>(0) {

  override fun knobPositionToValue(knobPosition: Int): Int {
    return if (sliderWidth > 0) Math.round(360 * knobPosition.toFloat() / sliderWidth) else 0
  }

  override fun valueToKnobPosition(value: Int): Int = Math.round(value / 360f * sliderWidth)

  override fun slide(shift: Int): Int = max(0, min(value + shift * SLIDE_UNIT, 360))

  override fun paintSlider(g2d: Graphics2D) {
    g2d.paint = LinearGradientPaint(Point2D.Double(0.0, 0.0), Point2D.Double(sliderWidth.toDouble(), 0.0), POINTS, COLORS)
    g2d.fillRect(leftPadding, topPadding, sliderWidth, height - topPadding - bottomPadding)
  }
}
