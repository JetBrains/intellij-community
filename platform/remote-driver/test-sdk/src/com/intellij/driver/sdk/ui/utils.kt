package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import java.awt.Point
import java.awt.Rectangle

fun Driver.hasFocus(c: Component) = utility(IJSwingUtilities::class).hasFocus(c)
fun Driver.hasFocus(c: UiComponent) = hasFocus(c.component)

val UiComponent.center: Point get() {
  val location = component.getLocationOnScreen()
  return Point(location.x + component.width / 2, location.y + component.height / 2)
}

val UiComponent.boundsOnScreen
  get() = component.let { c ->
    val locationOnScreen = c.getLocationOnScreen()
    Rectangle(locationOnScreen.x, locationOnScreen.y, c.width, c.height)
  }

val UiComponent.accessibleName: String? get() = component.getAccessibleContext()?.getAccessibleName()

@Remote("com.intellij.util.IJSwingUtilities")
interface IJSwingUtilities {
  fun hasFocus(c: Component): Boolean
}

@Remote("java.awt.Rectangle")
interface RectangleRef {
  fun contains(p: Point): Boolean
  fun getX(): Double
  fun getY(): Double
  fun getWidth(): Double
}
