package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote

@Remote("org.assertj.swing.core.Robot", plugin = REMOTE_ROBOT_MODULE_ID)
interface Robot {
  fun moveMouse(component: Component)
  fun moveMouse(component: Component, point: Point)
  fun moveMouse(point: Point)
  fun click(component: Component)

  //fun click(component: Component, mouseButton: MouseButton)
  //fun click(component: Component, mouseButton: MouseButton, counts: Int)
  fun click(component: Component, point: Point)
  fun pressAndReleaseKey(p0: Int, vararg p1: Int)
  fun pressModifiers(p0: Int)

  //fun pressMouse(mouseButton: MouseButton)
  fun pressMouse(component: Component, point: Point)

  //fun pressMouse(component: Component, point: Point, mouseButton: MouseButton)
  //fun pressMouse(point: Point, mouseButton: MouseButton)
  fun pressKey(p0: Int)
  fun releaseKey(p0: Int)
  fun type(char: Char)
  fun enterText(text: String)

  //fun releaseMouse(mouseButton: MouseButton)
  fun releaseMouseButtons()
  fun rightClick(component: Component)
  fun focus(component: Component)
  fun doubleClick(component: Component)
  fun cleanUpWithoutDisposingWindows()
  fun isReadyForInput(component: Component): Boolean
  fun focusAndWaitForFocusGain(component: Component)
  fun releaseModifiers(p0: Int)
  fun rotateMouseWheel(component: Component, p1: Int)
  fun rotateMouseWheel(p0: Int)
  fun pressAndReleaseKeys(vararg p0: Int)
  fun waitForIdle()
}