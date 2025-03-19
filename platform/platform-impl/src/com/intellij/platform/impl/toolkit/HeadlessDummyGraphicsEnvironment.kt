// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.impl.toolkit

import org.jetbrains.annotations.ApiStatus
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel

@ApiStatus.Internal
class HeadlessDummyGraphicsEnvironment: ClientGraphicsEnvironment {
  companion object {
    @JvmStatic
    val instance: HeadlessDummyGraphicsEnvironment = HeadlessDummyGraphicsEnvironment()
  }

  private val dummyDevice = object: GraphicsDevice() {
    val dummyGraphicsConfiguration = object: GraphicsConfiguration() {
      override fun getDevice() = getScreenDevices()[0]
      override fun getColorModel() = ColorModel.getRGBdefault()
      override fun getColorModel(transparency: Int) = ColorModel.getRGBdefault()
      override fun getDefaultTransform() = AffineTransform()
      override fun getNormalizingTransform() = AffineTransform()
      override fun getBounds() = Rectangle(0, 0, 1024, 768)

    }
    override fun getType() = TYPE_RASTER_SCREEN
    override fun getIDstring() = "DummyHeadless"
    override fun getConfigurations() = arrayOf(dummyGraphicsConfiguration)
    override fun getDefaultConfiguration() = dummyGraphicsConfiguration
  }

  override fun isInitialized(): Boolean = true
  override fun getNumScreens(): Int = 1
  override fun makeScreenDevice(id: Int): GraphicsDevice = dummyDevice
  override fun isDisplayLocal(): Boolean = false
  override fun getScreenDevices(): Array<GraphicsDevice> = arrayOf(dummyDevice)
}