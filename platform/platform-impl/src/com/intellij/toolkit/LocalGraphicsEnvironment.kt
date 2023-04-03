// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.toolkit

import org.jetbrains.annotations.ApiStatus
import sun.awt.PlatformGraphicsInfo
import sun.java2d.SunGraphicsEnvironment
import java.awt.GraphicsDevice

@ApiStatus.Internal
class LocalGraphicsEnvironment: ClientGraphicsEnvironment {
  private val localGraphicsEnvironment: SunGraphicsEnvironment? = if (!IdeGraphicEnvironment.isRealHeadless) PlatformGraphicsInfo.createGE() as SunGraphicsEnvironment else null

  override fun isInitialized() = true
  override fun getNumScreens(): Int {
    localGraphicsEnvironment?.let {
      it::class.java.getDeclaredMethod("getNumScreens").apply {
        isAccessible = true
        return invoke(localGraphicsEnvironment) as Int
      }
    }
    return HeadlessDummyGraphicsEnvironment.instance.getNumScreens()
  }

  override fun makeScreenDevice(id: Int): GraphicsDevice {
    localGraphicsEnvironment?.let {
      it::class.java.getDeclaredMethod("makeScreenDevice", Int::class.java).apply {
        isAccessible = true
        return invoke(localGraphicsEnvironment, id) as GraphicsDevice
      }
    }
    return HeadlessDummyGraphicsEnvironment.instance.makeScreenDevice(id)
  }

  override fun isDisplayLocal(): Boolean {
    localGraphicsEnvironment?.let {
      it::class.java.getDeclaredMethod("isDisplayLocal").apply {
        isAccessible = true
        return invoke(localGraphicsEnvironment) as Boolean
      }
    }
    return HeadlessDummyGraphicsEnvironment.instance.isDisplayLocal()
  }

  override fun getScreenDevices(): Array<GraphicsDevice> = localGraphicsEnvironment?.screenDevices ?: HeadlessDummyGraphicsEnvironment.instance.getScreenDevices()
}