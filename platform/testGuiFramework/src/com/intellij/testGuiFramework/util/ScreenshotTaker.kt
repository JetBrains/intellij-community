// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

import com.intellij.openapi.util.io.FileUtil.ensureExists
import com.intellij.testGuiFramework.framework.GuiTestPaths
import com.intellij.testGuiFramework.impl.GuiTestNameHolder
import org.fest.swing.core.BasicComponentPrinter
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter

enum class ScreenshotDestination { Screenshots, Failures }

object ScreenshotTaker {

  private val robot = Robot()

  private fun safeTakeScreenshotAndSave(file: File,
                                captureArea: Rectangle = FULL_SCREEN,
                                format: ImageFormat = ImageFormat.JPG,
                                compressionQuality: Float = 0.5f) =
    try {
      writeCompressed(takeScreenshot(captureArea), file, format, compressionQuality)
    }
    catch (e: IOException) {
      logError("Failed to create screenshot '${file.absolutePath}'", e)
    }

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
    }
    finally {
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

  private val FULL_SCREEN: Rectangle = Rectangle(Toolkit.getDefaultToolkit().screenSize)

  private fun getFileNameTemplate(): String {
    val dateFormat = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss.SSS")
    val date = Date()
    return dateFormat.format(date) //2016/11/16 12:08:43
  }

  fun getFolder(destination: ScreenshotDestination = ScreenshotDestination.Screenshots): File {
    val toFolder = when(destination){
      ScreenshotDestination.Screenshots -> GuiTestPaths.testScreenshotDirPath.toString()
      ScreenshotDestination.Failures -> GuiTestPaths.failedTestScreenshotDir.toString()
    }
    val toFile = Paths.get (toFolder, GuiTestNameHolder.testName).toFile()
    ensureExists(toFile)
    return toFile
  }

  fun take(screenshotName: String = "", destinations: Set<ScreenshotDestination> = setOf(
    ScreenshotDestination.Screenshots)) {
    destinations.forEach {
      val file = getOrCreateScreenshotFile(screenshotName, it)
      safeTakeScreenshotAndSave(file)
    }
  }

  fun takeHierarchy(screenshotName: String = "", destinations: Set<ScreenshotDestination> = setOf(
    ScreenshotDestination.Screenshots)) {
    destinations.forEach {
      val file = File(getFolder(it), "${getFileName(screenshotName)}.hierarchy.txt")
      try {
        file.writeText(getHierarchy())
      }
      catch (e: IOException) {
        logError("Failed to create hierarchy  file '${file.absolutePath}'. ${e.message}")
      }
    }
  }

  fun takeScreenshotAndHierarchy(screenshotName: String = "", destinations: Set<ScreenshotDestination> = setOf(
    ScreenshotDestination.Screenshots)) {
    take(screenshotName, destinations)
    takeHierarchy(screenshotName, destinations)
  }

  private fun getFileName(screenshotName: String) =
    "${getFileNameTemplate()}${if (screenshotName.isNotEmpty()) "_$screenshotName" else ""}"

  private fun getOrCreateScreenshotFile(screenshotName: String, destination: ScreenshotDestination = ScreenshotDestination.Screenshots): File {
    val file = File(getFolder(destination), "${getFileName(screenshotName)}.jpg")
    file.delete()
    return file
  }

  fun getHierarchy(): String {
    val out = ByteArrayOutputStream()
    val printStream = PrintStream(out, true)
    val componentPrinter = BasicComponentPrinter.printerWithCurrentAwtHierarchy()
    componentPrinter.printComponents(printStream)
    printStream.flush()
    return String(out.toByteArray())
  }

}