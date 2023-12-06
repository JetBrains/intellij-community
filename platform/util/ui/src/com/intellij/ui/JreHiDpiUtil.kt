// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.isHiDPIEnabledAndApplicable
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicReference

object JreHiDpiUtil {
  private val jreHiDPI = AtomicReference<Boolean>()

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics configuration represents a HiDPI device.
   * (Analogue of [com.intellij.util.ui.UIUtil.isRetina] on macOS)
   */
  @JvmStatic
  fun isJreHiDPI(gc: GraphicsConfiguration?): Boolean = isHiDPIEnabledAndApplicable(sysScale(gc))

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the specified scaling level corresponds to a HiDPI device.
   * (Analogue of [com.intellij.util.ui.UIUtil.isRetina] on macOS)
   */
  @JvmStatic
  fun isJreHiDPI(sysScale: Float): Boolean = isHiDPIEnabledAndApplicable(sysScale)

  /**
   * Returns whether the JRE-managed HiDPI mode is enabled and the graphics represents a HiDPI device.
   * (Analogue of [com.intellij.util.ui.UIUtil.isRetina] on macOS)
   */
  @JvmStatic
  fun isJreHiDPI(g: Graphics2D?): Boolean = isHiDPIEnabledAndApplicable(sysScale(g))

  @JvmStatic
  fun isJreHiDPIEnabled(): Boolean {
    if (SystemInfoRt.isMac) {
      return true
    }

    var value = jreHiDPI.get()
    if (value != null) {
      return value
    }

    synchronized(jreHiDPI) {
      value = jreHiDPI.get()
      if (value != null) {
        return value
      }
      value = false
      if (java.lang.Boolean.parseBoolean(System.getProperty("hidpi", "true")) && SystemInfo.isJetBrainsJvm) {
        try {
          val graphicsEnvironment = GraphicsEnvironment.getLocalGraphicsEnvironment()
          val sunGraphicsEnvironmentClass = Class.forName("sun.java2d.SunGraphicsEnvironment")
          if (sunGraphicsEnvironmentClass.isInstance(graphicsEnvironment)) {
            try {
              val method = sunGraphicsEnvironmentClass.getDeclaredMethod("isUIScaleEnabled")
              method.isAccessible = true
              value = method.invoke(graphicsEnvironment) as Boolean
            }
            catch (e: NoSuchMethodException) {
              value = false
            }
          }
        }
        catch (ignore: Throwable) {
        }
      }
      jreHiDPI.set(value)
    }
    return value
  }

  @Suppress("FunctionName")
  @TestOnly
  fun test_jreHiDPI(): AtomicReference<Boolean> {
    isJreHiDPIEnabled() // force init
    return jreHiDPI
  }
}
