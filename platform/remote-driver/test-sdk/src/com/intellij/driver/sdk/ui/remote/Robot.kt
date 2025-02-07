package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.model.RemoteMouseButton
import java.awt.Point

@Remote("com.jetbrains.performancePlugin.remotedriver.robot.SmoothRobot", plugin = REMOTE_ROBOT_MODULE_ID)
interface Robot {
  fun moveMouse(component: Component)
  fun moveMouse(component: Component, point: Point)
  fun moveMouse(point: Point)
  fun click(component: Component)

  fun click(component: Component, mouseButton: RemoteMouseButton)
  fun click(component: Component, mouseButton: RemoteMouseButton, counts: Int)
  fun click(component: Component, point: Point)
  fun click(point: Point, button: RemoteMouseButton, times: Int)
  fun click(component: Component, point: Point, button: RemoteMouseButton, times: Int)
  fun pressAndReleaseKey(keyCode: Int, vararg modifiers: Int)
  fun pressModifiers(modifierMask: Int)

  fun pressMouse(mouseButton: RemoteMouseButton)
  fun pressMouse(component: Component, point: Point)

  fun pressMouse(component: Component, point: Point, mouseButton: RemoteMouseButton)
  fun pressMouse(point: Point, mouseButton: RemoteMouseButton)
  fun pressKey(keyCode: Int)
  fun doubleKey(keyCode: Int)
  fun doublePressKeyAndHold(key: Int)
  fun releaseKey(keyCode: Int)
  fun type(char: Char)
  fun enterText(text: String)

  fun releaseMouse(mouseButton: RemoteMouseButton)
  fun releaseMouseButtons()
  fun rightClick(component: Component)
  fun focus(component: Component)
  fun doubleClick(component: Component)
  fun cleanUpWithoutDisposingWindows()
  fun isReadyForInput(component: Component): Boolean
  fun focusAndWaitForFocusGain(component: Component)
  fun releaseModifiers(modifierMask: Int)
  fun rotateMouseWheel(component: Component, amount: Int)
  fun rotateMouseWheel(amount: Int)
  fun pressAndReleaseKeys(vararg keyCodes: Int)
  fun waitForIdle()
  fun selectAndDrag(component: Component, to: Point, from: Point, delayMs: Int)

  fun getColor(component: Component, point: Point?): ColorRef
}