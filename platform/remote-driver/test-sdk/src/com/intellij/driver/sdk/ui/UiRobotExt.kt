package com.intellij.driver.sdk.ui

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.jdk.getSystemProperty
import com.intellij.driver.sdk.ui.components.UiComponent
import java.awt.Point
import java.awt.event.KeyEvent

fun UiRobot.pasteText(text: String) {
  driver.copyToClipboard(text)

  keyboard {
    val keyEvent = if (driver.getSystemProperty("os.name").lowercase().startsWith("mac")) KeyEvent.VK_META else KeyEvent.VK_CONTROL
    hotKey(keyEvent, KeyEvent.VK_V)
  }
}

fun UiRobot.dragAndDrop(start: Point, end: Point) {
  try {
    moveMouse(start)
    Thread.sleep(300)
    robot.pressMouse(RemoteMouseButton.LEFT)
    Thread.sleep(500)
    moveMouse(end)
    Thread.sleep(500)
  }
  finally {
    robot.releaseMouse(RemoteMouseButton.LEFT)
  }
}

fun UiComponent.dragAndDrop(to: Point) {
  driver.ui.dragAndDrop(this.center, to)
}
