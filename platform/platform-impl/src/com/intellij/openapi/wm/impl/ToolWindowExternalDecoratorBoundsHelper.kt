// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.registry.Registry
import java.awt.Frame
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Helper class for ensuring that the OS and/or JVM doesn't mess with the bounds until the window is shown.
 *
 *  Often the environment changes the bounds of a window unpredictably with no good reason.
 *  This happens most often on Linux and macOS, and on macOS it's even outside the EDT.
 *  To avoid this, we keep track of the window bounds set through the API only,
 *  and then, when the window is shown, and for a short time interval after that,
 *  we keep resetting the bounds to the last set ones in case of any external change.
 *
 *  This makes it impossible to move windows from the OS side for a while,
 *  but since the interval is very short, it shouldn't become a UX problem.
 *
 * @param decorator The tool window external decorator to manage bounds for.
 */
internal class ToolWindowExternalDecoratorBoundsHelper(private val decorator: ToolWindowExternalDecorator) {

  private var isUpdatingBounds = false

  var bounds = decorator.bounds
    set(value) {
      if (!isUpdatingBounds) { // prevent recursive calls
        field = value
      }
    }

  private var shownAt: Long? = null

  private val listener = MyWindowListener()

  init {
    decorator.window.addComponentListener(listener)
  }

  inner class MyWindowListener : ComponentAdapter() {
    override fun componentMoved(e: ComponentEvent?) {
      checkBounds()
    }

    override fun componentResized(e: ComponentEvent?) {
      checkBounds()
    }

    override fun componentShown(e: ComponentEvent?) {
      shownAt = System.currentTimeMillis()
      checkBounds()
    }

  }

  private fun checkBounds() {
    val sinceShown = sinceShown() ?: return
    val maxDelay = Registry.intValue("ide.tool.window.prevent.move.resize.timeout", 100)
    if (maxDelay < 0) {
      return // disabled
    }
    if (sinceShown > maxDelay) {
      decorator.window.removeComponentListener(listener)
      return
    }
    val storedBounds = bounds
    val actualBounds = decorator.bounds
    if (storedBounds != actualBounds) {
      if (decorator.isMaximized) {
        decorator.log().debug {
          "The tool window ${decorator.id} external (${decorator.getToolWindowType()}) decorator " +
          "is shown with the bounds $actualBounds, " +
          "the expected bounds are $storedBounds, $sinceShown ms after showing, " +
          "NOT re-applying because the frame is currently maximized"
        }
        return
      }
      decorator.log().warn(
        "The tool window ${decorator.id} external (${decorator.getToolWindowType()}) decorator " +
        "is shown with the bounds $actualBounds, " +
        "but the expected bounds are $storedBounds, $sinceShown ms after showing, re-applying"
      )
      try {
        isUpdatingBounds = true
        decorator.bounds = storedBounds
      }
      finally {
        isUpdatingBounds = false
      }
    }
    else {
      decorator.log().debug {
        "The tool window ${decorator.id} external (${decorator.getToolWindowType()}) decorator " +
        "is shown with the bounds $actualBounds, $sinceShown ms after showing, matching the expected bounds"
      }
    }
  }

  private fun sinceShown(): Long? = shownAt?.let {System.currentTimeMillis() - it }

}

private val ToolWindowExternalDecorator.isMaximized: Boolean
  get() = (window as? Frame)?.extendedState == Frame.MAXIMIZED_BOTH
