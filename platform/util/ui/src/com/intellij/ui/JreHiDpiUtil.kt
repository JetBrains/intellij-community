// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.isHiDPIEnabledAndApplicable
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.awt.Graphics2D
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object JreHiDpiUtil {
  private val jreHiDPI = AtomicReference<Boolean>()
  private val PRELOADED = AtomicBoolean()
  private val INIT_STACK_TRACE = AtomicReference<Throwable>()

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
      if (SystemInfoRt.isMac || StartupUiUtil.isWaylandToolkit()) {
        value = true
      }
      else if (java.lang.Boolean.parseBoolean(System.getProperty("hidpi", "true")) && SystemInfo.isJetBrainsJvm) {
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
      INIT_STACK_TRACE.set(Throwable("JreHiDpiUtil is first initialized here"))
      jreHiDPI.set(value)
    }
    return value
  }

  @ApiStatus.Internal
  fun preload() {
    if (!PRELOADED.compareAndSet(false, true)) return
    val initStackTrace = INIT_STACK_TRACE.get()
    if (initStackTrace != null) {
      thisLogger().error(Throwable("Must be not computed before that call", initStackTrace))
    }
    isJreHiDPIEnabled()
  }

  @Suppress("FunctionName")
  @TestOnly
  fun test_jreHiDPI(): AtomicReference<Boolean> {
    isJreHiDPIEnabled() // force init
    return jreHiDPI
  }
}
