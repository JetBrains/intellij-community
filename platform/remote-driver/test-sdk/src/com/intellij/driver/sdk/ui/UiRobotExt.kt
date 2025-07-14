package com.intellij.driver.sdk.ui

import com.intellij.driver.client.Remote
import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.jdk.getSystemProperty
import com.intellij.driver.sdk.ui.components.UiComponent
import java.awt.Point
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyEvent

fun UiRobot.pasteText(text: String) {
  driver.utility(ToolkitRef::class)
    .getDefaultToolkit()
    .getSystemClipboard()
    .setContents(driver.new(StringSelectionRef::class, text), null)

  keyboard {
    val keyEvent = if (driver.getSystemProperty("os.name").lowercase().startsWith("mac")) KeyEvent.VK_META else KeyEvent.VK_CONTROL
    hotKey(keyEvent, KeyEvent.VK_V)
  }
}

fun UiRobot.getClipboardText(): Object = driver.utility(ToolkitRef::class)
    .getDefaultToolkit()
    .getSystemClipboard()
    .getData(DataFlavor.stringFlavor)

fun UiRobot.dragAndDrop(start: Point, end: Point) {
  try {
    moveMouse(start)
    Thread.sleep(300)
    robot.pressMouse(RemoteMouseButton.LEFT)
    Thread.sleep(500)
    moveMouse(end)
    Thread.sleep(500)
  } finally {
    robot.releaseMouse(RemoteMouseButton.LEFT)
  }
}

fun UiComponent.dragAndDrop(to: Point) {
  driver.ui.dragAndDrop(this.center, to)
}

@Remote("java.awt.Toolkit")
interface ToolkitRef {
  fun getDefaultToolkit(): ToolkitRef
  fun getSystemClipboard(): ClipboardRef
}

@Remote("java.awt.datatransfer.Clipboard")
interface ClipboardRef {
  fun setContents(content: StringSelectionRef, ownerRef: ClipboardOwnerRef?)
  fun getData(flavor: DataFlavor): Object
}

@Remote("java.awt.datatransfer.ClipboardOwner")
interface ClipboardOwnerRef

@Remote("java.awt.datatransfer.StringSelection")
interface StringSelectionRef
