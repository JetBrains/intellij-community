package com.intellij.driver.sdk.ui

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.ui.components.UiComponent
import java.awt.Point
import java.awt.event.KeyEvent

fun UiRobot.pasteText(text: String) {
  driver.copyToClipboard(text)

  keyboard {
    hotKeyWithDefaultModifierKey(KeyEvent.VK_V)
  }
}

@Deprecated("Does not work on Wayland. Use dragAndDrop(UiComponent, Point?, UiComponent, Point?) instead.",
            ReplaceWith("dragAndDrop(from, fromPoint, to, toPoint)"))
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

@Deprecated("Does not work on Wayland. Use dragAndDrop(UiComponent, Point?, UiComponent, Point?) instead.",
            ReplaceWith("dragAndDrop(from, fromPoint, to, toPoint)"))
fun UiComponent.dragAndDrop(to: Point) {
  driver.ui.dragAndDrop(this.center, to)
}

fun UiRobot.dragAndDrop(from: UiComponent, fromPoint: Point? = null, to: UiComponent, toPoint: Point? = null) {
  robot.dragAndDrop(
    from.component,
    fromPoint ?: from.component.let { Point(it.width / 2, it.height / 2) },
    to.component,
    toPoint ?: to.component.let { Point(it.width / 2, it.height / 2) }
  )
}
