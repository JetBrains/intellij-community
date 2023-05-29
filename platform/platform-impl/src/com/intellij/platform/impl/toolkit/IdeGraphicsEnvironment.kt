// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.PlatformGraphicsInfo
import sun.java2d.SunGraphicsEnvironment
import java.awt.Rectangle

@Internal
class IdeGraphicsEnvironment: SunGraphicsEnvironment() {
  companion object {
    @JvmStatic
    val instance: IdeGraphicsEnvironment = IdeGraphicsEnvironment()

    @JvmStatic
    val isRealHeadless
      get() = PlatformGraphicsInfo.getDefaultHeadlessProperty();

    @JvmStatic
    private fun getClientInstance(): ClientGraphicsEnvironment {
      val application = ApplicationManager.getApplication()
      if (application == null) {
        return HeadlessDummyGraphicsEnvironment.instance
      }
      val client = try {
        ClientGraphicsEnvironment.getInstance()
      }
      catch (ex: IllegalStateException) {   // service could be not loaded yet
        HeadlessDummyGraphicsEnvironment.instance
      }
      if (!client.isInitialized()) {
        return HeadlessDummyGraphicsEnvironment.instance
      }
      return client
    }
  }

  init {
    // Because JBR in some cases changes this property to false in the `SunGraphicsEnvironment` constructor
    System.setProperty("swing.bufferPerWindow", true.toString())
  }

  fun notifyDevicesChanged() = displayChanger.notifyListeners()
  fun findGraphicsConfigurationFor(bounds: Rectangle) = getClientInstance().findGraphicsConfigurationFor(bounds)
  override fun getNumScreens() = getClientInstance().getNumScreens()
  override fun makeScreenDevice(screennum: Int) = getClientInstance().makeScreenDevice(screennum)
  override fun isDisplayLocal() = false
  override fun getScreenDevices() = getClientInstance().getScreenDevices()
}