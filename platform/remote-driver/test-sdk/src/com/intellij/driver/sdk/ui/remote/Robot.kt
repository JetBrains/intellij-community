package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.model.RemoteMouseButton
import java.awt.Point

@Remote("com.jetbrains.performancePlugin.remotedriver.robot.IdeRobot", plugin = REMOTE_ROBOT_MODULE_ID)
interface Robot {
  fun hasInputFocus(): Boolean
  fun moveMouse(component: Component)
  fun moveMouse(component: Component, point: Point)
  fun moveMouse(point: Point)
  fun click(component: Component)

  fun click(component: Component, mouseButton: RemoteMouseButton)
  fun click(component: Component, mouseButton: RemoteMouseButton, counts: Int)
  fun click(component: Component, point: Point)
  fun click(point: Point, button: RemoteMouseButton)
  fun click(point: Point, button: RemoteMouseButton, times: Int)
  fun click(component: Component, point: Point, button: RemoteMouseButton)
  fun click(component: Component, point: Point, button: RemoteMouseButton, times: Int)
  fun pressAndReleaseKey(keyCode: Int, vararg modifiers: Int)
  fun dragAndDrop(from: Component, fromPoint: Point, to: Component, toPoint: Point)
  fun pressModifiers(modifierMask: Int)
  /**
   * Performs a strict click in the context of SmoothRobot by repeating the full
   * mouse cycle until a `mouseClicked` event is observed.
   *
   * How it differs from `clickWithRetry`:
   * - `clickWithRetry` treats any of `mousePressed`/`mouseReleased`/`mouseClicked` as success
   *   and stops retrying once any of these events fires.
   * - `strictClickWithRetry` is stricter: it only considers `mouseClicked` as success and
   *   will keep retrying the full press→release sequence until that event is received
   *   (or attempts are exhausted).
   *
   * Why this is needed: some components trigger their action only when the release happens
   * within the same target. If we stop on `pressed` or `released` alone, the intended action may not
   * happen.
   *
   * !!! Don't use strictClick if the component should disappear after mouse release.
   */
  fun strictClick(component: Component, point: Point?)

  /**
   * Enqueues a click addressed to [component] — `times` x (pressed, released, clicked) sourced at the component
   * itself, at coordinates relative to it ([where] `== null` — its centre) — and returns without waiting.
   *
   * For a click whose effect cannot be awaited: a click that opens a modal dialog blocks the dispatching thread
   * while the dialog is up, so any robot call that waits for its own event to be dispatched deadlocks. Unlike
   * [click], the recipient is named rather than derived from a screen position, so a popup or balloon appearing in
   * between cannot take the gesture.
   *
   * Nothing beyond `isShowing` and `isEnabled` is guaranteed: delivery is not observed (it happens after this
   * returns), the aim goes stale if the component moves, a post into a modally blocked window is dropped
   * silently, and nothing orders it against later driver calls — use `IdeEventQueue.flushQueue` as an explicit
   * barrier. See the platform-side `IdeRobot.postGesture` KDoc and AT-5091.
   */
  fun postGesture(component: Component, where: Point?, button: RemoteMouseButton, times: Int)

  fun moveMouseAndPress(component: Component, where: Point?)
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
  /**
   * Performs a left mouse triple-click on the given component.
   *
   * Triple-click is commonly used to select a whole line or statement.

   * @param c the target component to receive the triple-click
   */
  fun tripleClick(component: Component)
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