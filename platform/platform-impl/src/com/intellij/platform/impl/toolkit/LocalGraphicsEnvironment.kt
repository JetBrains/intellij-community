// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import org.jetbrains.annotations.ApiStatus
import sun.awt.PlatformGraphicsInfo
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

@ApiStatus.Internal
class LocalGraphicsEnvironment: ClientGraphicsEnvironment {
  private val platformGraphicsEnvironment: GraphicsEnvironment? =
    if (!IdeGraphicsEnvironment.isRealHeadless) PlatformGraphicsInfo.createGE() else null

  private val lookup = MethodHandles.lookup()
  override fun isInitialized() = true
  
  override fun getNumScreens(): Int {
    val methodType = MethodType.methodType(Int::class.java)
    platformGraphicsEnvironment?.let {
      return lookup.findVirtual(it::class.java, "getNumScreens", methodType).invoke(it) as Int
    }
    return HeadlessDummyGraphicsEnvironment.instance.getNumScreens()
  }

  override fun makeScreenDevice(id: Int): GraphicsDevice {
    val methodType = MethodType.methodType(GraphicsDevice::class.java, Int::class.java)
    platformGraphicsEnvironment?.let {
      return lookup.findVirtual(it::class.java, "makeScreenDevice", methodType).invoke(it, id) as GraphicsDevice
    }
    return HeadlessDummyGraphicsEnvironment.instance.makeScreenDevice(id)
  }

  override fun isDisplayLocal(): Boolean {
    val methodType = MethodType.methodType(Boolean::class.java)
    platformGraphicsEnvironment?.let {
      return lookup.findVirtual(it::class.java, "isDisplayLocal", methodType).invoke(it) as Boolean
    }
    return HeadlessDummyGraphicsEnvironment.instance.isDisplayLocal()
  }

  override fun getScreenDevices(): Array<GraphicsDevice> = platformGraphicsEnvironment?.screenDevices ?: HeadlessDummyGraphicsEnvironment.instance.getScreenDevices()
}