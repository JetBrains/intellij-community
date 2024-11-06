package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.openapi.diagnostic.fileLogger
import java.awt.Point
import java.awt.Rectangle

fun Driver.hasFocus(c: Component) = utility(IJSwingUtilities::class).hasFocus(c)
fun Driver.hasFocus(c: UiComponent) = hasFocus(c.component)

fun Driver.requestFocusFromIde(project: Project?) {
  fileLogger().info("Requesting focus from IDE for project: $project")
  withContext(OnDispatcher.EDT) {
    utility(ProjectUtil::class).focusProjectWindow(project, true)
  }
}

@Remote(value = "com.intellij.ide.impl.ProjectUtil")
interface ProjectUtil {
  fun focusProjectWindow(project: Project?, stealFocusIfAppInactive: Boolean)
}

@Remote("com.intellij.ide.IdeEventQueue")
interface IdeEventQueue {
  fun getInstance(): IdeEventQueue
  fun flushQueue()
}

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

val Rectangle.center get() = Point(getCenterX().toInt(), getCenterY().toInt())

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

  fun setSelectedOption(option: String)
}

@Remote("org.assertj.swing.driver.CellRendererReader")
interface CellRendererReader

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.AccessibleNameCellRendererReader", plugin = REMOTE_ROBOT_MODULE_ID)
interface AccessibleNameCellRendererReader: CellRendererReader
