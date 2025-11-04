// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.ComponentUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Rectangle
import java.awt.Window
import javax.swing.SwingUtilities

@ApiStatus.Internal
fun getPopupParentBounds(popup: Component): Rectangle? {
  if (!popup.isShowing) {
    LOG.warn("Impossible to determine the valid bounds because the popup is not showing: $popup")
    return null
  }
  val validBounds = getNearestTopLevelParentBounds(popup) ?: return null
  LOG.debug { "The allowed bounds in screen coordinates are $validBounds" }
  val directParent = popup.parent
  if (directParent == null) {
    LOG.warn("Impossible to determine the valid bounds because the popup has no direct parent: $popup")
    return null
  }
  // Now convert the allowed bounds to the direct parent's coordinate system.
  val directParentLocation = directParent.locationOnScreen
  validBounds.x -= directParentLocation.x
  validBounds.y -= directParentLocation.y
  LOG.debug { "The allowed bounds in parent coordinates are $validBounds" }
  return validBounds
}

@ApiStatus.Internal
fun moveToFitChildPopupX(childBounds: Rectangle, parent: Component) {
  if (!parent.isShowing) {
    LOG.warn("Impossible to fit the child popup to the main window because the parent is not showing: $parent")
    return
  }

  val childLocation = childBounds.location
  LOG.debug { "The initial child bounds are $childBounds" }
  SwingUtilities.convertPointToScreen(childLocation, parent)
  LOG.debug { "The initial child location relative to the screen is $childLocation" }

  val topLevelBounds = getNearestTopLevelParentBounds(parent) ?: return

  val parentLocation = parent.location
  LOG.debug { "The relative parent location is $parentLocation" }
  SwingUtilities.convertPointToScreen(parentLocation, parent.parent)
  val parentBounds = Rectangle(parentLocation, parent.size)
  LOG.debug { "The screen parent bounds are $parentBounds" }

  childLocation.x = fitValue(
    location = childLocation.x,
    width = childBounds.width,
    start1 = topLevelBounds.x,
    end1 = parentBounds.x,
    start2 = parentBounds.x + parentBounds.width,
    end2 = topLevelBounds.x + topLevelBounds.width,
    preferLess = childLocation.x < parentBounds.x + parentBounds.width / 2,
  )

  SwingUtilities.convertPointFromScreen(childLocation, parent)
  childBounds.location = childLocation
  LOG.debug { "The final result is $childBounds" }
}

private fun getNearestTopLevelParentBounds(component: Component): Rectangle? {
  // Can't use ComponentUtil.findUltimateParent() because we need the nearest non-popup window,
  // as it's what Wayland considers to be the owner of the popup.
  val topLevelWindow = ComponentUtil.findParentByCondition(component) { c ->
    c is Window && c.type != Window.Type.POPUP
  }
  if (topLevelWindow !is Window) { // pretty much a non-null check with a smart cast
    LOG.warn("The top level parent isn't a window, but $topLevelWindow")
    return null
  }
  val topLevelBounds = Rectangle(topLevelWindow.locationOnScreen, topLevelWindow.size)
  LOG.debug { "The top level bounds are $topLevelBounds" }
  return topLevelBounds
}

private fun fitValue(location: Int, width: Int, start1: Int, end1: Int, start2: Int, end2: Int, preferLess: Boolean): Int {
  LOG.debug { "The available intervals are $start1..$end1 and $start2..$end2, the popup size is $width" }
  if (location >= start1 && location + width < end1) {
    LOG.debug { "The initial location already fits within the first interval" }
    return location
  }
  if (location >= start2 && location + width < end2) {
    LOG.debug { "The initial location already fits within the second interval" }
    return location
  }
  val space1 = end1 - start1
  val space2 = end2 - start2
  if (space1 >= width && space2 >= width) {
    LOG.debug { "We have enough space on both sides, preferring the ${if (preferLess) "first" else "second"}" }
    return if (preferLess) end1 - width else start2
  }
  else if (space1 >= width) {
    val result = end1 - width
    LOG.debug { "We have enough space the first side: $space1 >= $width, the result is $end1-$width=$result" }
    return result
  }
  else if (space2 >= width) {
    LOG.debug { "We have enough space the first side: $space2 >= $width, the result is $start2" }
    return start2
  }
  else if (space1 > 0 && space1 > space2) {
    LOG.debug { "We have more space the first side: $space1 > $space2, the result is $start1" }
    return start1
  }
  else if (space2 > 0) {
    val result = end2 - width
    LOG.debug { "We have more space the second side: $space1 <= $space2, the result is $end2-$width=$result" }
    return result
  }
  else {
    LOG.debug { "We don't have any space at all, falling back to the initial value" }
    return location
  }
}

private val LOG = fileLogger()
