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
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.colorpicker

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import com.intellij.ui.picker.ColorListener
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import sun.awt.image.ToolkitImage
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.ColorModel
import java.awt.image.MemoryImageSource
import javax.swing.JComponent
import kotlin.math.ceil

private val KNOB_COLOR = Color.WHITE
private const val KNOB_RADIUS = 4

class SaturationBrightnessComponent(private val myModel: ColorPickerModel) : JComponent(), ColorListener, ColorPipette.Callback {
  var brightness = 1f
    private set
  var hue = 1f
    private set
  var saturation = 0f
    private set
  var alpha: Int = 255
    private set
  var pipetteMode = false
  val robot = Robot()

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
    if (Registry.`is`("ide.color.picker.new.pipette")) {
      myModel.addPipetteListener(this)
    }
  }

  private fun handleMouseEvent(e: MouseEvent) {
    myModel.setColor(getColorByPoint(e.point), this)
  }

  public fun getColorByPoint(p: Point): Color {
    val x = Math.max(0, Math.min(p.x, size.width))
    val y = Math.max(0, Math.min(p.y, size.height))

    val saturation = x.toFloat() / size.width
    val brightness = 1.0f - y.toFloat() / size.height

    val argb = ahsbToArgb(alpha, hue, saturation, brightness)
    val newColor = Color(argb, true)
    return newColor
  }

  override fun getPreferredSize(): Dimension = JBUI.size(PICKER_PREFERRED_WIDTH, 150)

  override fun getMinimumSize(): Dimension = JBUI.size(150, 140)

  private fun paintPipetteMode(graphics: Graphics) {
    graphics.color = parent.background
    graphics.fillRect(0,0, width, height)
    val g = graphics.create() as Graphics2D
    val p = MouseInfo.getPointerInfo().location
    val size = width / 21.0
    val img = robot.createMultiResolutionScreenCapture(Rectangle(p.x - 10, p.y - 5, 21, 11))
    val image = img.resolutionVariants.last()
    val iW = image.getWidth(null)
    val iH = image.getHeight(null)
    g.scale(width / 21.0, width / 21.0)
    g.drawImage(image, -((iW - 21) / 2.0).toInt(), -ceil((iH - 11) / 2.0).toInt(), null)
    g.dispose()
    val xx = ceil(size * 10).toInt()
    val yy = ceil(size * 5).toInt()
    graphics.color = Color.white
    graphics.drawRect(xx, yy, (size - 1).toInt(), (size - 1).toInt())
    graphics.color = Color.black
    graphics.drawRect(xx+1, yy+1, (size - 3).toInt(), (size - 3).toInt())
  }

  override fun paintComponent(g: Graphics) {
    if (Registry.`is`("ide.color.picker.new.pipette") && pipetteMode) {
      paintPipetteMode(g)
      return
    }
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
    g.drawOval(knobX - JBUI.scale(KNOB_RADIUS),
               knobY - JBUI.scale(KNOB_RADIUS),
               JBUI.scale(KNOB_RADIUS * 2),
               JBUI.scale(KNOB_RADIUS * 2))
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

  override fun picked(pickedColor: Color) {
    pipetteMode = false
  }

  override fun update(updatedColor: Color) {
    pipetteMode = true
    repaint()
  }

  override fun cancel() {
    pipetteMode = false
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
