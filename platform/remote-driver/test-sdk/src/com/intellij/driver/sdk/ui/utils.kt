package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.impl.RefWrapper
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.waitFor
import com.intellij.openapi.diagnostic.fileLogger
import java.awt.Point
import java.awt.Rectangle
import java.awt.datatransfer.DataFlavor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Driver.hasFocus(c: Component) = utility(IJSwingUtilities::class).hasFocus(c)
fun Driver.hasFocus(c: UiComponent) = hasFocus(c.component)

fun Driver.requestFocusFromIde(project: Project?) {
  fileLogger().info("Requesting focus from IDE for project: $project")
  withContext(OnDispatcher.EDT) {
    utility(ProjectUtil::class).focusProjectWindow(project, true)
  }
}

fun Driver.copyToClipboard(text: String) {
  getSystemClipboard().setContents(new(StringSelectionRef::class, text), null)
}

fun UiComponent.pasteText(text: String) {
  driver.copyToClipboard(text)
  driver.invokeAction($$"$Paste", component = component)
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

val UiComponent.center: Point
  get() {
    val location = component.getLocationOnScreen()
    return Point(location.x + component.width / 2, location.y + component.height / 2)
  }

val UiComponent.boundsOnScreen
  get() = component.let { c ->
    val locationOnScreen = c.getLocationOnScreen()
    Rectangle(locationOnScreen.x, locationOnScreen.y, c.width, c.height)
  }

val UiComponent.accessibleName: String? get() = component.getAccessibleContext()?.getAccessibleName()

val Component.rdTarget get() = (this as RefWrapper).getRef().rdTarget()

fun UiComponent.waitEnabled(timeout: Duration = 5.seconds) = apply {
  waitFor("'$accessibleName' component is enabled", timeout) {
    isEnabled()
  }
}

fun UiComponent.shouldHaveFocus(message: String? = null, timeout: Duration = 5.seconds) = apply {
  waitFor(message ?: "'$accessibleName' component has focus", timeout) {
    driver.hasFocus(this)
  }
}

@Remote("com.intellij.util.IJSwingUtilities")
interface IJSwingUtilities {
  fun hasFocus(c: Component): Boolean
}

@Remote("javax.swing.SwingUtilities")
interface SwingUtilities {
  fun computeDifference(rectA: Rectangle, rectB: Rectangle): Array<Rectangle>
}

val Rectangle.center: Point get() = Point(centerX.toInt(), centerY.toInt())

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

fun Driver.getClipboardText(): Any = getSystemClipboard().getData(DataFlavor.stringFlavor)

fun Driver.getSystemClipboard(): ClipboardRef = utility(ToolkitRef::class)
  .getDefaultToolkit()
  .getSystemClipboard()

@Remote("org.assertj.swing.driver.CellRendererReader")
interface CellRendererReader

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.AccessibleNameCellRendererReader", plugin = REMOTE_ROBOT_MODULE_ID)
interface AccessibleNameCellRendererReader : CellRendererReader

@Remote("java.awt.Toolkit")
interface ToolkitRef {
  fun getDefaultToolkit(): ToolkitRef
  fun getSystemClipboard(): ClipboardRef
}

@Remote("java.awt.datatransfer.Clipboard")
interface ClipboardRef {
  fun setContents(content: StringSelectionRef, ownerRef: ClipboardOwnerRef?)
  fun getData(flavor: DataFlavor): Any
}

@Remote("java.awt.datatransfer.ClipboardOwner")
interface ClipboardOwnerRef

@Remote("java.awt.datatransfer.StringSelection")
interface StringSelectionRef
