// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UndesirableClassUsage")

package com.intellij.ui

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.ProjectSelfieUtil.readImage
import com.intellij.platform.ProjectSelfieUtil.writeImage
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.io.DigestUtil
import com.intellij.util.loadImageForStartUp
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.*
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * To customize your IDE splash go to YourIdeNameApplicationInfo.xml and edit 'logo' tag. For more information, see documentation for
 * the tag attributes in ApplicationInfo.xsd file.
 */
class Splash(private val image: BufferedImage) : Dialog(null as Frame?,
                                               "splash" /* not visible, but available through window properties on Linux */) {
  init {
    isUndecorated = true
    background = Gray.TRANSPARENT
    // makes tiling window managers on a Linux show window as floating
    isResizable = false
    focusableWindowState = false
    val width = image.width
    val height = image.height
    size = Dimension(width, height)
    setLocationInTheCenterOfScreen(this)

    StartUpMeasurer.addInstantEvent("splash shown")
    runActivity("splash set visible") {
      isVisible = true
    }
    toFront()
  }

  override fun paint(g: Graphics) {
    StartupUiUtil.drawImage(g, image, 0, 0, null)
  }
}

@OptIn(DelicateCoroutinesApi::class)
internal fun loadSplashImage(appInfo: ApplicationInfoEx): BufferedImage {
  val scale = if (JreHiDpiUtil.isJreHiDPIEnabled()) sysScale() * scale(1f) else scale(1f)
  val file = try {
    getCacheFile(scale = scale, appInfo = appInfo)
  }
  catch (e: Throwable) {
    logger<Splash>().warn(e)
    null
  }

  if (file != null) {
    loadImageFromCache(file)?.let {
      return it
    }
  }

  val path = appInfo.splashImageUrl
  val result = doLoadImage(path, scale) ?: throw IllegalStateException("Cannot find image: $path")
  if (file != null) {
    GlobalScope.launch(Dispatchers.IO) {
      try {
        val rawImage = (if (result is JBHiDPIScaledImage) result.delegate else result) as BufferedImage
        writeImage(file = file, image = rawImage, sysScale = scale)
      }
      catch (e: Throwable) {
        logger<Splash>().warn("Cannot save splash image", e)
      }
    }
  }
  return result
}

private fun doLoadImage(path: String, scale: Float): BufferedImage? {
  val originalImage = loadImageForStartUp(requestedPath = path, scale = scale, classLoader = Splash::class.java.classLoader) ?: return null
  val w = originalImage.width
  val h = originalImage.height
  val resultImage = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  val g2 = resultImage.createGraphics()
  g2.composite = AlphaComposite.Src
  ImageUtil.applyQualityRenderingHints(g2)
  @Suppress("UseJBColor")
  g2.color = Color.WHITE
  val cornerRadius = 8 * scale
  g2.fill(RoundRectangle2D.Float(0f, 0f, w.toFloat(), h.toFloat(), cornerRadius, cornerRadius))
  g2.composite = AlphaComposite.SrcIn
  g2.drawImage(originalImage, 0, 0, null)
  g2.dispose()
  return ImageUtil.ensureHiDPI(resultImage, ScaleContext.create()) as BufferedImage
}

private fun setLocationInTheCenterOfScreen(window: Window) {
  val graphicsConfiguration = window.graphicsConfiguration
  val bounds = graphicsConfiguration.bounds
  if (SystemInfoRt.isWindows) {
    JBInsets.removeFrom(bounds, ScreenUtil.getScreenInsets(graphicsConfiguration))
  }
  window.location = StartupUiUtil.getCenterPoint(bounds, window.size)
}

private fun loadImageFromCache(file: Path): BufferedImage? {
  try {
    return readImage(file, ScaleContext::create)
  }
  catch (e: Throwable) {
    // don't use `error`, because it can crash application
    logger<Splash>().warn("Failed to load splash image", e)
  }
  return null
}

private fun getCacheFile(scale: Float, appInfo: ApplicationInfoEx): Path {
  val path = appInfo.splashImageUrl

  val d = DigestUtil.sha256()
  d.update(path.toByteArray(StandardCharsets.UTF_8))
  val buffer = ByteBuffer.allocate(java.lang.Long.SIZE + 1).order(ByteOrder.LITTLE_ENDIAN)
  // the path for EAP and release builds is the same, but content maybe different
  buffer.put((if (appInfo.isEAP) 1 else 0).toByte())

  // for dev run build data is equal to run time
  if (appInfo.build.isSnapshot) {
    var size: Long = 0
    try {
      val pathToSplash = if (path.startsWith('/')) path.substring(1) else path
      val resource = Splash::class.java.classLoader.getResource(pathToSplash)
      if (resource != null) {
        size = Files.size(Path.of(resource.toURI()))
      }
    }
    catch (e: Throwable) {
      logger<Splash>().warn("Failed to read splash image", e)
    }
    buffer.putLong(size)
  }
  else {
    buffer.putLong(appInfo.buildDate.timeInMillis)
  }
  buffer.flip()
  d.update(buffer)
  val encodedDigest = BigInteger(1, d.digest()).toString(Character.MAX_RADIX)
  return Path.of(PathManager.getSystemPath(), "splash", "$encodedDigest.$scale.ij")
}

