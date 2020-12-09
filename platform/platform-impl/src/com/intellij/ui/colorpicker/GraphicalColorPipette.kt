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

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.awt.image.ImageObserver
import javax.swing.*

/**
 * The size of captured screen area. It is same as the number of pixels are caught.<br>
 * The selected pixel is the central one, so this value must be odd.
 */
private const val SCREEN_CAPTURE_SIZE = 11
/**
 * The size of zoomed rectangle which shows the captured screen.
 */
private const val ZOOM_RECTANGLE_SIZE = 64

private val PIPETTE_BORDER_COLOR = Color.BLACK
private val INDICATOR_BOUND_COLOR = Color.RED
/**
 * The left/top bound of selected pixel in zoomed rectangle.
 */
private const val INDICATOR_BOUND_START = ZOOM_RECTANGLE_SIZE * (SCREEN_CAPTURE_SIZE / 2) / SCREEN_CAPTURE_SIZE
/**
 * The width/height of selected pixel in zoomed rectangle.
 */
private const val INDICATOR_BOUND_SIZE = ZOOM_RECTANGLE_SIZE * (SCREEN_CAPTURE_SIZE / 2 + 1) / SCREEN_CAPTURE_SIZE - INDICATOR_BOUND_START

private val TRANSPARENT_COLOR = Color(0, true)

private const val CURSOR_NAME = "GraphicalColorPicker"

/**
 * Duration of updating the color of current hovered pixel. The unit is millisecond.
 */
private const val DURATION_COLOR_UPDATING = 33

/**
 * The [ColorPipette] which picks up the color from monitor.
 */
open class GraphicalColorPipette(private val parent: JComponent) : ColorPipette {
  override val icon: Icon = AllIcons.Ide.Pipette

  override val rolloverIcon: Icon = AllIcons.Ide.Pipette_rollover

  override val pressedIcon: Icon = AllIcons.Ide.Pipette_rollover

  override fun pick(callback: ColorPipette.Callback) = PickerDialog(parent, callback).pick()
}

class GraphicalColorPipetteProvider : ColorPipetteProvider {
  override fun createPipette(owner: JComponent): ColorPipette = GraphicalColorPipette(owner)
}

private class PickerDialog(val parent: JComponent, val callback: ColorPipette.Callback) : ImageObserver {

  private val timer = Timer(DURATION_COLOR_UPDATING) { updatePipette() }
  private val center = Point(ZOOM_RECTANGLE_SIZE / 2, ZOOM_RECTANGLE_SIZE / 2)
  private val zoomRect = Rectangle(0, 0, ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE)
  private val captureRect = Rectangle()
  private var disposable = newDisposable()
  private fun newDisposable() = Disposer.newDisposable("Color Pipette")

  private val maskImage = ImageUtil.createImage(ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE, BufferedImage.TYPE_INT_ARGB)
  private val magnifierImage = ImageUtil.createImage(ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE, BufferedImage.TYPE_INT_ARGB)

  private val image: BufferedImage = let {
    val image = parent.graphicsConfiguration.createCompatibleImage(ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE, Transparency.TRANSLUCENT)
    val graphics2d = image.graphics as Graphics2D
    graphics2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
    image
  }

  private val robot = Robot()
  private var previousColor: Color? = null
  private var previousLoc: Point? = null

  private val picker: Dialog = let {
    val owner = SwingUtilities.getWindowAncestor(parent)

    val pickerFrame = when (owner) {
      is Dialog -> JDialog(owner)
      is Frame -> JDialog(owner)
      else -> JDialog(JFrame())
    }

    pickerFrame.isUndecorated = true
    pickerFrame.isAlwaysOnTop = true
    pickerFrame.size = Dimension(ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE)
    pickerFrame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

    val rootPane = pickerFrame.rootPane
    rootPane.putClientProperty("Window.shadow", false)
    rootPane.border = JBUI.Borders.empty()

    val mouseAdapter = object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        e.consume()
        when {
          SwingUtilities.isLeftMouseButton(e) -> pickDone()
          SwingUtilities.isRightMouseButton(e) -> cancelPipette()
          else -> Unit
        }
      }

      override fun mouseMoved(e: MouseEvent) = updatePipette()
    }

    pickerFrame.addMouseListener(mouseAdapter)
    pickerFrame.addMouseMotionListener(mouseAdapter)

    pickerFrame.addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
          KeyEvent.VK_ESCAPE -> cancelPipette()
          KeyEvent.VK_ENTER -> pickDone()
        }
      }
    })

    pickerFrame
  }

  init {
    val maskG = maskImage.createGraphics()
    maskG.color = Color.BLUE
    maskG.fillRect(0, 0, ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE)

    maskG.color = Color.RED
    maskG.composite = AlphaComposite.SrcOut
    maskG.fillRect(0, 0, ZOOM_RECTANGLE_SIZE, ZOOM_RECTANGLE_SIZE)
    maskG.dispose()
  }

  fun pick() {
    if (Registry.`is`("ide.color.picker.new.pipette")) {
      timer.start()
      disposable = newDisposable()
      ApplicationManager.getApplication().invokeLater {
        IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.EventDispatcher {
          if (it is MouseEvent) {
            if (it.clickCount > 0) {
              it.consume()
              when {
                SwingUtilities.isLeftMouseButton(it) -> pickDone()
                SwingUtilities.isRightMouseButton(it) -> cancelPipette()
                else -> Unit
              }
            }
            else if (it.id == MouseEvent.MOUSE_MOVED) {
              updatePipette();
            }
          }
          if (it is KeyEvent && it.id == KeyEvent.KEY_PRESSED) {
            when (it.keyCode) {
              KeyEvent.VK_ESCAPE -> it.consume().also { cancelPipette() }
              KeyEvent.VK_ENTER -> it.consume().also { pickDone() }
              KeyEvent.VK_UP -> moveMouse(it, 0, -1)
              KeyEvent.VK_DOWN -> moveMouse(it, 0, +1)
              KeyEvent.VK_LEFT -> moveMouse(it, -1, 0)
              KeyEvent.VK_RIGHT -> moveMouse(it, +1, 0)
            }
          }
          false
        }, disposable)
      }
    }
    else {
      picker.isVisible = true
      timer.start()
      // it seems like it's the lowest value for opacity for mouse events to be processed correctly
      WindowManager.getInstance().setAlphaModeRatio(picker, if (SystemInfo.isMac) 0.95f else 0.99f)
    }
  }

  private fun moveMouse(e: KeyEvent, x: Int, y: Int) {
    val p = MouseInfo.getPointerInfo().location
    robot.mouseMove(p.x + x, p.y + y)
    e.consume()
  }

  override fun imageUpdate(img: Image, flags: Int, x: Int, y: Int, width: Int, height: Int) = false

  private fun cancelPipette() {
    timer.stop()

    picker.isVisible = false
    picker.dispose()
    Disposer.dispose(disposable)
    callback.cancel()
  }

  private fun pickDone() {
    timer.stop()

    val pointerInfo = MouseInfo.getPointerInfo()
    val location = pointerInfo.location
    val pickedColor = robot.getPixelColor(location.x, location.y)
    picker.isVisible = false
    Disposer.dispose(disposable)
    callback.picked(pickedColor)
  }

  private fun updatePipette() {
    if (Registry.`is`("ide.color.picker.new.pipette")) {
      val pointerInfo = MouseInfo.getPointerInfo()
      val mouseLoc = pointerInfo.location
      val pickedColor = robot.getPixelColor(mouseLoc.x, mouseLoc.y)
      if (previousLoc != mouseLoc || previousColor != pickedColor) {
        previousLoc = mouseLoc
        previousColor = pickedColor
        callback.update(pickedColor)
      }
    }
    else {
      if (picker.isShowing) {
        val pointerInfo = MouseInfo.getPointerInfo()
        val mouseLoc = pointerInfo.location
        picker.setLocation(mouseLoc.x - picker.width / 2, mouseLoc.y - picker.height / 2)

        val pickedColor = robot.getPixelColor(mouseLoc.x, mouseLoc.y)

        if (previousLoc != mouseLoc || previousColor != pickedColor) {
          previousLoc = mouseLoc
          previousColor = pickedColor

          val halfPixelNumber = SCREEN_CAPTURE_SIZE / 2
          captureRect.setBounds(mouseLoc.x - halfPixelNumber, mouseLoc.y - halfPixelNumber, SCREEN_CAPTURE_SIZE, SCREEN_CAPTURE_SIZE)

          val capture = robot.createScreenCapture(captureRect)

          val graphics = image.graphics as Graphics2D

          // Clear the cursor graphics
          graphics.composite = AlphaComposite.Src
          graphics.color = TRANSPARENT_COLOR
          graphics.fillRect(0, 0, image.width, image.height)

          graphics.drawImage(capture, zoomRect.x, zoomRect.y, zoomRect.width, zoomRect.height, this)

          // cropping round image
          graphics.composite = AlphaComposite.DstOut
          graphics.drawImage(maskImage, zoomRect.x, zoomRect.y, zoomRect.width, zoomRect.height, this)

          // paint magnifier
          graphics.composite = AlphaComposite.SrcOver
          graphics.drawImage(magnifierImage, 0, 0, this)

          graphics.composite = AlphaComposite.SrcOver
          graphics.color = PIPETTE_BORDER_COLOR
          graphics.drawRect(0, 0, ZOOM_RECTANGLE_SIZE - 1, ZOOM_RECTANGLE_SIZE - 1)
          graphics.color = INDICATOR_BOUND_COLOR
          graphics.drawRect(INDICATOR_BOUND_START, INDICATOR_BOUND_START, INDICATOR_BOUND_SIZE, INDICATOR_BOUND_SIZE)

          picker.cursor = parent.toolkit.createCustomCursor(image, center, CURSOR_NAME)

          callback.update(pickedColor)
        }
      }
    }
  }
}
