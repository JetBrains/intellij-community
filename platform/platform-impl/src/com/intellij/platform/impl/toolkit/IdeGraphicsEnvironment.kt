// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.platform.impl.toolkit

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.client.ClientSessionsManager
import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.PlatformGraphicsInfo
import sun.java2d.SunGraphicsEnvironment
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle

@Internal
class IdeGraphicsEnvironment: SunGraphicsEnvironment() {
  companion object {
    @JvmStatic
    val instance: IdeGraphicsEnvironment = IdeGraphicsEnvironment()

    @JvmStatic
    val isRealHeadless: Boolean
      get() = PlatformGraphicsInfo.getDefaultHeadlessProperty()

    @JvmStatic
    private fun getClientInstance(): ClientGraphicsEnvironment {
      val application = ApplicationManager.getApplication()
      if (application == null) {
        return HeadlessDummyGraphicsEnvironment.instance
      }
      val session = ClientSessionsManager.getAppSessions(ClientKind.CONTROLLER).firstOrNull()
                    ?: ClientSessionsManager.getAppSessions(ClientKind.LOCAL).firstOrNull()
                    ?: return HeadlessDummyGraphicsEnvironment.instance
      val client = try {
        ClientGraphicsEnvironment.getInstance(session)
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

  fun notifyDevicesChanged(): Unit = displayChanger.notifyListeners()
  fun findGraphicsConfigurationFor(bounds: Rectangle): GraphicsConfiguration = getClientInstance().findGraphicsConfigurationFor(bounds)
  override fun getNumScreens(): Int = getClientInstance().getNumScreens()
  override fun makeScreenDevice(screennum: Int): GraphicsDevice = getClientInstance().makeScreenDevice(screennum)
  override fun isDisplayLocal(): Boolean = false
  override fun getScreenDevices(): Array<GraphicsDevice> = getClientInstance().getScreenDevices()
}