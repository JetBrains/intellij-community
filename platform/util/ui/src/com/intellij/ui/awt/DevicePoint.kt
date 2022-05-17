package com.intellij.ui.awt

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.GraphicsConfiguration
import java.awt.Point
import java.awt.event.MouseEvent
import kotlin.math.roundToInt

/**
 * Represents a location in device coordinates. Useful for comparing locations across screens with different DPI scale factors
 */
class DevicePoint {

  val point: Point

  constructor(locationOnScreen: Point, sourceComponent: Component) {
    point = if (requiresScaling()) {
      val scale = JBUIScale.sysScale(sourceComponent)
      val screenOrigin = sourceComponent.graphicsConfiguration.bounds.location
      scale(locationOnScreen, screenOrigin, scale)
    }
    else {
      locationOnScreen.location
    }
  }

  constructor(event: MouseEvent) : this(event.locationOnScreen, event.component)

  fun getLocationOnScreen(destinationComponent: Component) = getLocationOnScreen(destinationComponent.graphicsConfiguration)

  fun getLocationOnScreen(destinationGraphicsConfiguration: GraphicsConfiguration): Point {
    return if (requiresScaling()) {
      val scale = JBUIScale.sysScale(destinationGraphicsConfiguration)
      // Screen bounds is in screen coordinates, but the origin is in device coordinates. We don't need width/height
      val screenOrigin = destinationGraphicsConfiguration.bounds.location
      scale(point, screenOrigin, 1 / scale)
    }
    else {
      point.location  // Return a copy
    }
  }

  private fun requiresScaling(): Boolean {
    return if (SystemInfoRt.isLinux ||  // JRE-managed HiDPI mode is not yet implemented (pending)
               SystemInfoRt.isMac) {    // JRE-managed HiDPI mode is permanent
      false
    }
    else JreHiDpiUtil.isJreHiDPIEnabled() // device space equals user space
  }

  private fun scale(point: Point, screenOrigin: Point, factor: Float): Point {
    return Point((screenOrigin.x + (point.x - screenOrigin.x) * factor).roundToInt(),
                 (screenOrigin.y + (point.y - screenOrigin.y) * factor).roundToInt())
  }

  override fun toString() = javaClass.name + "[x=" + point.x + ",y=" + point.y + "]"
}