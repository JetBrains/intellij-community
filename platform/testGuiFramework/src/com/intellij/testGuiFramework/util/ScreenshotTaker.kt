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
  private val cursorImg: BufferedImage = loadCursor()

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
    image.graphics.drawImage(cursorImg,
                             cursorLocation.x - cursorImg.width / 2,
                             cursorLocation.y - cursorImg.height / 2,
                             null)
    return image
  }

  private fun loadCursor(): BufferedImage =
    this::class.java.getResourceAsStream("/images/cursor.png").use { inputStream -> return ImageIO.read(inputStream) }

  enum class ImageFormat(val formatName: String) {
    PNG("png"),
    JPG("jpg")
  }

  companion object {
    private val FULL_SCREEN: Rectangle = Rectangle(Toolkit.getDefaultToolkit().screenSize)
    private val LOG: Logger = Logger.getLogger(ScreenshotTaker::class.java)
  }
}