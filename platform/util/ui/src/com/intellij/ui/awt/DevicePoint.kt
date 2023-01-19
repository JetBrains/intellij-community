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
 * Represents a location in native device coordinates. Useful for comparing locations across screens with different DPI scale factors
 *
 * Mouse events are in the coordinate system of the screen that contains the component receiving the events. E.g. a laptop with a built-in
 * screen that has a native resolution of 2880✗1800 might have a 200% DPI factor applied, and would therefore use a coordinate system of
 * 1440✗900. A 1080p external monitor at 100% DPI would have a coordinate system of 1920✗1080, but an origin of (2880,0). A component on the
 * external monitor would receive a mouse event located on the built-in screen in the external monitor's coordinate system, such as
 * (2874,1566), even though this location is outside the 1440✗900 scaled bounds of the built-in screen. A component on the built-in screen
 * would receive the same event scaled to (1437✗783). This obviously causes problems comparing locations across screens, such as for drag
 * and drop - the drag events will be in the originating component's coordinate system, and need to be converted to the drop target's
 * coordinate system before a reliable comparison can be made.
 *
 * This class will convert a given [Point] into the native coordinate system, and convert back to a given component's screen coordinate
 * system to allow appropriate comparisons.
 */
class DevicePoint {

  val point: Point

  constructor(locationOnScreen: Point, sourceComponent: Component) {
    point = if (requiresScaling()) {
      // Make sure the component's graphicsConfiguration is not null (because it hasn't been added to a container yet). This should always
      // be true when constructed from a MouseEvent or RelativePoint. But let's not allocate strings
      if (sourceComponent.graphicsConfiguration == null) {
        throw Error("Component's graphics configuration is null. Ensure component is added to a container. $sourceComponent")
      }
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
   * Converts the device point to a screen point, using the screen that contains the point as the destination screen coordinate system.
   *
   * Do not use the returned value for comparison, as it is unpredictable that the returned value will be in the expected screen coordinate
   * system. Use one of the [getLocationOnScreenFromPoint] overloads instead.
   */
  val locationOnScreen: Point
    get() = if (requiresScaling()) getLocationOnScreenFromPoint(point) else Point(point)

  /**
   * Converts the device point to the screen coordinates of the given component
   *
   * @param destinationComponent the component to use to get the destination screen coordinates
   * @return the device point in screen coordinates
   */
  fun getLocationOnScreen(destinationComponent: Component): Point {
    if (destinationComponent.graphicsConfiguration != null) {
      return getLocationOnScreen(destinationComponent.graphicsConfiguration)
    }

    // The component doesn't have a graphics configuration, which most likely means it hasn't been added to a container. We can't guarantee
    // that the returned point is in the correct coordinate system. Asserting or throwing just pushes checks to all callers, so try to find
    // the best coordinate system. The point will always be in the correct screen, as native and scaled coordinate systems do not allow for
    // overlap, but the point might end up in the wrong coordinate system - it might be scaled to a different screen than the caller is
    // trying to request.
    // Essentially, if we get this far, we've got a bug in the caller that means the component is no longer part of a container, and is
    // not a good candidate to be using for a location comparison. But this is the wrong place to assert that bug.

    // Try to get the destination screen based on the location of the component, which is only valid when the component is showing
    if (destinationComponent.isShowing) {
      return getLocationOnScreenFromPoint(destinationComponent.locationOnScreen)
    }

    // We can't figure out the requested destination screen, so just use the point's own screen's coordinate system. We don't know if this
    // is worthwhile, but if we don't have a valid destination screen, it's unlikely the returned point will match anything successfully.
    return Point(point)
  }

  /**
   * Converts the device point to the screen coordinates of the given component
   *
   * @param destinationGraphicsConfiguration the [GraphicsConfiguration] of the destination screen
   * @return the device point in screen coordinates
   */
  fun getLocationOnScreen(destinationGraphicsConfiguration: GraphicsConfiguration): Point {
    return if (requiresScaling()) {
      val factor = JBUIScale.sysScale(destinationGraphicsConfiguration)
      // The width and height of the screen device's configuration are in screen coordinates, but the location is in device coordinates
      val screenOrigin = destinationGraphicsConfiguration.bounds.location
      scale(point, screenOrigin, 1 / factor)
    }
    else {
      Point(point)
    }
  }

  private fun getLocationOnScreenFromPoint(destinationPoint: Point): Point {
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.forEach { device ->
      val gc = device.defaultConfiguration
      val factor = JBUIScale.sysScale(gc)
      val deviceBounds = gc.bounds.also {
        it.width = (it.width * factor).roundToInt()
        it.height = (it.height * factor).roundToInt()
      }
      if (deviceBounds.contains(destinationPoint)) {
        return scale(point, deviceBounds.location, 1 / factor)
      }
    }
    return Point(point)
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