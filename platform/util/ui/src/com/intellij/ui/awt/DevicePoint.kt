package com.intellij.ui.awt

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
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
  constructor(relativePoint: RelativePoint) : this(relativePoint.screenPoint, relativePoint.component)

  /**
   * Converts the device point to a screen point, using the screen that contains the point as the destination screen
   */
  val locationOnScreen: Point
    get() {
      if (requiresScaling()) {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.forEach { device ->
          val gc = device.defaultConfiguration
          val factor = JBUIScale.sysScale(gc)
          val deviceBounds = gc.bounds.also {
            it.width = (it.width * factor).roundToInt()
            it.height = (it.height * factor).roundToInt()
          }
          if (deviceBounds.contains(point)) {
            return scale(point, deviceBounds.location, 1 / factor)
          }
        }
      }

      return point.location // Return a copy
    }

  /**
   * Converts the device point to the screen coordinates of the given component
   *
   * @param destinationComponent the component to use to get the destination screen coordinates
   * @return the device point in screen coordinates
   */
  fun getLocationOnScreen(destinationComponent: Component) = getLocationOnScreen(destinationComponent.graphicsConfiguration)

  /**
   * Converts the device point to the screen coordinates of the given component
   *
   * @param destinationGraphicsConfiguration the [GraphicsConfiguration] of the destination screen
   * @return the device point in screen coordinates
   */
  fun getLocationOnScreen(destinationGraphicsConfiguration: GraphicsConfiguration): Point {
    return if (requiresScaling()) {
      val factor = JBUIScale.sysScale(destinationGraphicsConfiguration)
      // Screen bounds is in screen coordinates, but the origin is in device coordinates. We don't need width/height
      val screenOrigin = destinationGraphicsConfiguration.bounds.location
      scale(point, screenOrigin, 1 / factor)
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

  /**
   * Creates a [RelativePoint] from the current location, relative to the passed component
   *
   * Required because a [RelativePoint] is specific to the source component's screen's scaling factor, and cannot be used outside the source
   * component's hierarchy.
   *
   * @param component the component that this point should be made relative to. This component's screen's scaling factor is used
   * @return a [RelativePoint] representing the location, relative to the given component, in the given component's screen's coordinate system
   */
  fun toRelativePoint(component: Component): RelativePoint {
    val location = getLocationOnScreen(component)
    SwingUtilities.convertPointFromScreen(location, component)
    return RelativePoint(component, location)
  }

  private fun scale(point: Point, screenOrigin: Point, factor: Float): Point {
    return Point((screenOrigin.x + (point.x - screenOrigin.x) * factor).roundToInt(),
                 (screenOrigin.y + (point.y - screenOrigin.y) * factor).roundToInt())
  }

  override fun toString() = javaClass.name + "[x=" + point.x + ",y=" + point.y + "]"
}