package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import java.awt.Point

fun Driver.hasFocus(c: Component) = utility(IJSwingUtilities::class).hasFocus(c)
fun Driver.hasFocus(c: UiComponent) = hasFocus(c.component)

val UiComponent.center: Point get() {
  val location = component.getLocationOnScreen()
  return Point(location.x + component.width / 2, location.y + component.height / 2)
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

fun printableString(toPrint: String): String {
  val resultString = toPrint.let {
    val maxLength = 600
    if (it.length < maxLength) {
      it
    }
    else {
      it.take(maxLength) + "..."
    }
  }
  return resultString
}

fun Driver.setRegistry(key: String, value: String) {
  utility(Registry::class).get(key).setValue(value)
}

@Remote("com.intellij.openapi.util.registry.Registry")
interface Registry {
  fun get(key: String): RegistryValue
}

@Remote("com.intellij.openapi.util.registry.RegistryValue")
interface RegistryValue {
  fun setValue(value: String)
}