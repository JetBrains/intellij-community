// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

import org.apache.log4j.Logger
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter

class ScreenshotTaker(private val robot: Robot = Robot()) {

  fun safeTakeScreenshotAndSave(file: File,
                                captureArea: Rectangle = FULL_SCREEN,
                                format: ImageFormat = ImageFormat.JPG,
                                compressionQuality: Float = 0.5f) =
    try {
      writeCompressed(takeScreenshot(captureArea), file, format, compressionQuality)
    } catch (e: Exception) {
      LOG.error("screenshot failed", e)
    }

  fun safeTakeScreenshotAndSave(file: File,
                                component: Component,
                                format: ImageFormat = ImageFormat.JPG,
                                compressionQuality: Float = 0.5f) =
    safeTakeScreenshotAndSave(file, component.bounds, format, compressionQuality)

  private fun takeScreenshot(captureArea: Rectangle = FULL_SCREEN): BufferedImage =
    drawCursor(robot.createScreenCapture(captureArea), MouseInfo.getPointerInfo().location)

  private fun writeCompressed(image: BufferedImage, file: File, format: ImageFormat, compressionQuality: Float) {
    var writer: ImageWriter? = null
    try {
      ImageIO.createImageOutputStream(file).use { imageOutputStream ->
        writer = ImageIO.getImageWritersByFormatName(format.formatName).next()

        val params = writer!!.defaultWriteParam
        params.compressionMode = ImageWriteParam.MODE_EXPLICIT
        params.compressionQuality = compressionQuality

        writer!!.output = imageOutputStream
        writer!!.write(null, IIOImage(image, null, null), params)
      }
    } finally {
      writer?.dispose()
    }
  }

  private fun drawCursor(image: BufferedImage, cursorLocation: Point): BufferedImage {
    val graphics: Graphics = image.graphics
    graphics.color = Color.RED
    graphics.fillRect(cursorLocation.x - 10, cursorLocation.y, 20, 1)
    graphics.fillRect(cursorLocation.x, cursorLocation.y - 10, 1, 20)
    graphics.dispose()
    return image
  }

  enum class ImageFormat(val formatName: String) {
    PNG("png"),
    JPG("jpg")
  }

  companion object {
    private val FULL_SCREEN: Rectangle = Rectangle(Toolkit.getDefaultToolkit().screenSize)
    private val LOG: Logger = Logger.getLogger(ScreenshotTaker::class.java)
  }
}