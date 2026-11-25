package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.step

/**
 * Hides all tool windows of the frame.
 *
 * The `HideAllWindows` action restores the last layout when no tool window is visible, so it runs only when the frame
 * shows a tool window: an island of the islands UI or a tool window decorator of the classic UI.
 */
fun IdeaFrameUI.hideAllToolWindows(): Unit = step("Hide all tool windows") {
  val visible = xx {
    or(byType("com.intellij.toolWindow.xNext.island.XNextIslandHolder"), byType("com.intellij.toolWindow.InternalDecoratorImpl"))
  }.list()
  if (visible.isNotEmpty()) {
    driver.invokeAction("HideAllWindows")
  }
}

/** Hides all tool windows of the IDE frame. See [IdeaFrameUI.hideAllToolWindows]. */
fun Driver.hideAllToolWindows() {
  ideFrame { hideAllToolWindows() }
}
