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

import com.intellij.ui.picker.ColorListener
import java.awt.Color

val DEFAULT_PICKER_COLOR = Color(0xFF, 0xFF, 0xFF, 0xFF)

class ColorPickerModel(originalColor: Color = DEFAULT_PICKER_COLOR) {

  private val listeners = mutableSetOf<ColorListener>()

  var color: Color = originalColor
    private set

  fun setColor(newColor: Color, source: Any? = null) {
    color = newColor
    Color.RGBtoHSB(color.red, color.green, color.blue, hsb)

    listeners.forEach { it.colorChanged(color, source) }
  }

  private val hsb: FloatArray = Color.RGBtoHSB(color.red, color.green, color.blue, null)

  val red get() = color.red

  val green get() = color.green

  val blue get() = color.blue

  val alpha get() = color.alpha

  val hex: String get() = Integer.toHexString(color.rgb)

  val hue get() = hsb[0]

  val saturation get() = hsb[1]

  val brightness get() = hsb[2]

  fun addListener(listener: ColorListener) = listeners.add(listener)

  fun removeListener(listener: ColorListener) = listeners.remove(listener)
}
