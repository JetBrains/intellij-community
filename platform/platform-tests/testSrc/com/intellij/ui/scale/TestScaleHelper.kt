// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UndesirableClassUsage", "LiftReturnOrAssignment")

package com.intellij.ui.scale

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.icons.loadRasterImage
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.JBUIScale.setSystemScaleFactor
import com.intellij.ui.scale.JBUIScale.setUserScaleFactor
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.svg.renderSvg
import com.intellij.util.SystemProperties
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import java.awt.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JComponent
import kotlin.io.path.inputStream
import kotlin.math.ceil

internal object TestScaleHelper {
  private const val STANDALONE_PROP = "intellij.test.standalone"
  private val originalSysProps = HashMap<String, String?>()
  private val originalRegProps = HashMap<String, String>()
  private var originalUserScale = 0f
  private var originalSysScale = 0f
  private var originalJreHiDPIEnabled = false

  @BeforeClass
  @JvmStatic
  fun setState() {
    originalUserScale = scale(1f)
    originalSysScale = sysScale()
    originalJreHiDPIEnabled = JreHiDpiUtil.isJreHiDPIEnabled()
  }

  @AfterClass
  @JvmStatic
  fun restoreState() {
    setUserScaleFactor(originalUserScale)
    setSystemScaleFactor(originalSysScale)
    overrideJreHiDPIEnabled(originalJreHiDPIEnabled)
    restoreRegistryProperties()
    restoreSystemProperties()
  }

  @JvmStatic
  fun setRegistryProperty(key: String, value: String) {
    val prop = Registry.get(key)
    originalRegProps.putIfAbsent(key, prop.asString())
    prop.setValue(value)
  }

  @JvmStatic
  fun setSystemProperty(name: String, value: String?) {
    originalSysProps.putIfAbsent(name, System.getProperty(name))
    SystemProperties.setProperty(name, value)
  }

  @JvmStatic
  fun restoreProperties() {
    restoreSystemProperties()
    restoreRegistryProperties()
  }

  @JvmStatic
  fun restoreSystemProperties() {
    for ((key, value) in originalSysProps) {
      SystemProperties.setProperty(key, value)
    }
  }

  @JvmStatic
  fun restoreRegistryProperties() {
    for ((key, value) in originalRegProps) {
      Registry.get(key).setValue(value)
    }
  }

  @JvmStatic
  fun overrideJreHiDPIEnabled(enabled: Boolean) {
    JreHiDpiUtil.test_jreHiDPI().set(enabled)
  }

  @JvmStatic
  fun assumeStandalone() {
    Assume.assumeTrue("not in $STANDALONE_PROP mode", java.lang.Boolean.getBoolean(STANDALONE_PROP))
  }

  @Suppress("SpellCheckingInspection")
  @JvmStatic
  fun assumeHeadful() {
    Assume.assumeFalse("should not be headless", GraphicsEnvironment.isHeadless())
  }

  @JvmStatic
  fun createGraphics(scale: Double): Graphics2D {
    val g = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics()
    g.scale(scale, scale)
    return g
  }

  @JvmStatic
  fun createImageAndGraphics(scale: Double, width: Int, height: Int): Pair<BufferedImage, Graphics2D> {
    val image = BufferedImage(ceil(width * scale).toInt(), ceil(height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    val gScale: Double = if (JreHiDpiUtil.isJreHiDPIEnabled()) scale else 1.0
    g.scale(gScale, gScale)
    return Pair(image, g)
  }

  @JvmStatic
  fun createComponent(ctx: ScaleContext): JComponent {
    return object : JComponent() {
      val myGC = MyGraphicsConfiguration(ctx.getScale(ScaleType.SYS_SCALE))
      override fun getGraphicsConfiguration(): GraphicsConfiguration {
        return myGC
      }
    }
  }

  @JvmStatic
  fun saveImage(image: BufferedImage?, path: String) {
    try {
      ImageIO.write(image, "png", File(path))
    }
    catch (e: IOException) {
      e.printStackTrace()
    }
  }

  @JvmOverloads
  @JvmStatic
  fun loadImage(file: Path, scaleContext: ScaleContext = ScaleContext.createIdentity()): BufferedImage {
    val scale = scaleContext.getScale(DerivedScaleType.PIX_SCALE).toFloat()
    val path = file.toString()
    file.inputStream().use { inputStream ->
      if (path.endsWith(".svg")) {
        return renderSvg(inputStream = inputStream, scale = scale, path = path)
      }
      else {
        return loadRasterImage(stream = inputStream)
      }
    }
  }

  @JvmStatic
  fun msg(ctx: UserScaleContext): String = "[JRE-HiDPI ${JreHiDpiUtil.isJreHiDPIEnabled()}], $ctx"
}

private class MyGraphicsConfiguration(scale: Double) : GraphicsConfiguration() {
  private val tx = AffineTransform.getScaleInstance(scale, scale)

  override fun getDevice(): GraphicsDevice? = null

  override fun getColorModel(): ColorModel? = null

  override fun getColorModel(transparency: Int): ColorModel? = null

  override fun getDefaultTransform(): AffineTransform = tx

  override fun getNormalizingTransform(): AffineTransform = tx

  override fun getBounds(): Rectangle = Rectangle()
}
