// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.platform

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.util.lang.ByteBufferCleaner
import com.intellij.util.ui.ImageUtil
import sun.awt.image.SunWritableRaster
import java.awt.*
import java.awt.image.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.*
import java.util.*

internal object ProjectSelfieUtil {
  val isEnabled: Boolean
    get() = ExperimentalUI.isNewUI() && Registry.`is`("ide.project.loading.show.last.state", true)

  private fun getSelfieLocation(projectWorkspaceId: String): Path {
    return appSystemDir.resolve("project-selfies-v2").resolve("$projectWorkspaceId.ij")
  }

  fun readImage(file: Path, scaleContextProvider: () -> ScaleContext): Image? {
    val buffer = try {
      FileChannel.open(file).use { channel ->
        channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN)
      }
    }
    catch (ignore: NoSuchFileException) {
      return null
    }

    try {
      val intBuffer = buffer.asIntBuffer()
      val w = intBuffer.get()
      val h = intBuffer.get()

      val scaleContext = scaleContextProvider()

      val currentSysScale = scaleContext.getScale(ScaleType.SYS_SCALE).toFloat()
      val imageSysScale = java.lang.Float.intBitsToFloat(intBuffer.get())
      if (currentSysScale != imageSysScale) {
        logger<ProjectSelfieUtil>().warn("Project selfie is not used as scale differs (current: $currentSysScale, image: $imageSysScale)")
        return null
      }

      val dataBuffer = DataBufferInt(w * h)
      intBuffer.get(SunWritableRaster.stealData(dataBuffer, 0))
      SunWritableRaster.makeTrackable(dataBuffer)
      val colorModel = ColorModel.getRGBdefault() as DirectColorModel
      val raster = Raster.createPackedRaster(dataBuffer, w, h, w, colorModel.masks, Point(0, 0))

      @Suppress("UndesirableClassUsage")
      val rawImage = BufferedImage(colorModel, raster, false, null)
      return ImageUtil.ensureHiDPI(rawImage, scaleContext)
    }
    finally {
      ByteBufferCleaner.unmapBuffer(buffer)
    }
  }

  fun readProjectSelfie(value: String, device: GraphicsDevice): Image? {
    return readImage(getSelfieLocation(value), scaleContextProvider = {
      ScaleContext.create(device.defaultConfiguration)
    })
  }

  fun takeProjectSelfie(component: Component, workspaceId: String) {
    //val start = System.currentTimeMillis()
    val graphicsConfiguration = component.graphicsConfiguration
    val image = ImageUtil.createImage(graphicsConfiguration, component.width, component.height, BufferedImage.TYPE_INT_ARGB)
    UISettings.setupAntialiasing(image.graphics)

    component.paint(image.graphics)
    writeImage(file = getSelfieLocation(workspaceId), image = image, sysScale = JBUIScale.sysScale(graphicsConfiguration))

    //println("Write image: " + (System.currentTimeMillis() - start) + "ms")
  }

  fun writeImage(file: Path, image: BufferedImage, sysScale: Float) {
    val parent = file.parent
    Files.createDirectories(parent)
    val tempFile = Files.createTempFile(parent, file.fileName.toString(), ".ij")
    FileChannel.open(tempFile, EnumSet.of(StandardOpenOption.WRITE)).use { channel ->
      val imageData = (image.raster.dataBuffer as DataBufferInt).data

      val buffer = ByteBuffer.allocateDirect(imageData.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      try {
        buffer.putInt(image.width)
        buffer.putInt(image.height)
        buffer.putInt(java.lang.Float.floatToIntBits(sysScale))
        buffer.flip()
        do {
          channel.write(buffer)
        }
        while (buffer.hasRemaining())

        buffer.clear()

        buffer.asIntBuffer().put(imageData)
        buffer.position(0)
        do {
          channel.write(buffer)
        }
        while (buffer.hasRemaining())
      }
      finally {
        ByteBufferCleaner.unmapBuffer(buffer)
      }
    }

    try {
      Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE)
    }
    catch (e: AtomicMoveNotSupportedException) {
      Files.move(tempFile, file)
    }
  }
}