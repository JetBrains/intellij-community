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

import com.intellij.ui.ColorUtil
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import sun.awt.image.ToolkitImage
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.ColorModel
import java.awt.image.MemoryImageSource
import javax.swing.JComponent

private val KNOB_COLOR = Color.WHITE
private const val KNOB_OUTER_RADIUS = 4
private const val KNOB_INNER_RADIUS = 3

class SaturationBrightnessComponent(private val myModel: ColorPickerModel) : JComponent(), ColorListener {
  var brightness = 1f
    private set
  var hue = 1f
    private set
  var saturation = 0f
    private set
  var alpha: Int = 0
    private set

  init {
    isOpaque = false
    background = Color.WHITE

    val mouseAdapter = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        handleMouseEvent(e)
      }

      override fun mouseDragged(e: MouseEvent) {
        handleMouseEvent(e)
      }
    }
    addMouseListener(mouseAdapter)
    addMouseMotionListener(mouseAdapter)

    myModel.addListener(this)
  }

  private fun handleMouseEvent(e: MouseEvent) {
    val x = Math.max(0, Math.min(e.x, size.width))
    val y = Math.max(0, Math.min(e.y, size.height))

    val saturation = x.toFloat() / size.width
    val brightness = 1.0f - y.toFloat() / size.height

    val argb = ahsbToArgb(alpha, hue, saturation, brightness)
    myModel.setColor(Color(argb, true), this)
  }

  override fun getPreferredSize(): Dimension = JBUI.size(PICKER_PREFERRED_WIDTH, 150)

  override fun getMinimumSize(): Dimension = JBUI.size(150, 140)

  override fun paintComponent(g: Graphics) {
    val component = Rectangle(0, 0, size.width, size.height)
    val image = createImage(SaturationBrightnessImageProducer(size.width, size.height, hue))

    g.color = UIUtil.getPanelBackground()
    g.fillRect(0, 0, width, height)

    g.drawImage(image, component.x, component.y, null)

    val knobX = Math.round(saturation * component.width)
    val knobY = Math.round(component.height * (1.0f - brightness))

    if (image is ToolkitImage && image.bufferedImage.width > knobX && image.bufferedImage.height > knobY) {
      val rgb = image.bufferedImage.getRGB(knobX, knobY)
      g.color = if (ColorUtil.isDark(Color(rgb))) Color.WHITE else Color.BLACK
    } else {
      g.color = KNOB_COLOR
    }
    val config = GraphicsUtil.setupAAPainting(g)
    g.drawOval(knobX - JBUI.scale(KNOB_OUTER_RADIUS),
               knobY - JBUI.scale(KNOB_OUTER_RADIUS),
               JBUI.scale(KNOB_OUTER_RADIUS * 2),
               JBUI.scale(KNOB_OUTER_RADIUS * 2))
    g.drawOval(knobX - JBUI.scale(KNOB_INNER_RADIUS),
               knobY - JBUI.scale(KNOB_INNER_RADIUS),
               JBUI.scale(KNOB_INNER_RADIUS * 2),
               JBUI.scale(KNOB_INNER_RADIUS * 2))
    config.restore()
  }

  override fun colorChanged(color: Color, source: Any?) {
    val hsbValues = Color.RGBtoHSB(color.red, color.green, color.blue, null)
    setHSBAValue(hsbValues[0], hsbValues[1], hsbValues[2], color.alpha)
  }

  private fun setHSBAValue(h: Float, s: Float, b: Float, a: Int) {
    hue = h
    saturation = s
    brightness = b
    alpha = a
    repaint()
  }
}

private class SaturationBrightnessImageProducer(imageWidth: Int, imageHeight: Int, hue: Float)
  : MemoryImageSource(imageWidth, imageHeight, null, 0, imageWidth) {

  init {
    val saturation = FloatArray(imageWidth * imageHeight)
    val brightness = FloatArray(imageWidth * imageHeight)

    // create lookup tables
    for (x in 0 until imageWidth) {
      for (y in 0 until imageHeight) {
        val index = x + y * imageWidth
        saturation[index] = x.toFloat() / imageWidth
        brightness[index] = 1.0f - y.toFloat() / imageHeight
      }
    }

    val pixels = IntArray(imageWidth * imageHeight)
    newPixels(pixels, ColorModel.getRGBdefault(), 0, imageWidth)
    setAnimated(true)
    for (index in pixels.indices) {
      pixels[index] = Color.HSBtoRGB(hue, saturation[index], brightness[index])
    }
    newPixels()
  }
}
