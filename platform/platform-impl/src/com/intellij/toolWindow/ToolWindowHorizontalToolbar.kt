// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Point
import java.awt.Rectangle
import javax.swing.border.Border
import kotlin.math.max

/**
 * Used only when a [ToolWindowExtension] is present (see [ToolWindowPaneNewButtonManager])
 */
internal class ToolWindowHorizontalToolbar(paneId: String, anchor: ToolWindowAnchor, isPrimary: Boolean) :
  ToolWindowToolbar(isPrimary, anchor) {

  override val topStripe: StripeV2 = HorizontalStripe(this, paneId, anchor)

  // Not used by a horizontal bar (it hosts a single stripe); required by the base class. Never populated or added to the layout.
  override val bottomStripe: StripeV2 = HorizontalStripe(this, paneId, anchor)

  // Never added to the layout; the "more" button is only shown on the LEFT/RIGHT toolbars.
  override val moreButton: MoreSquareStripeButton = MoreSquareStripeButton(this, anchor)

  override val accessibleGroupName: String
    get() = if (anchor == ToolWindowAnchor.TOP) UIBundle.message("toolbar.group.top.accessible.group.name")
    else UIBundle.message("toolbar.group.bottom.accessible.group.name")

  init {
    init()
  }

  override fun init() {
    layout = BorderLayout()
    isOpaque = true
    background = JBUI.CurrentTheme.ToolWindow.stripeBackground()
    border = createBorder()
    topStripe.background = JBUI.CurrentTheme.ToolWindow.stripeBackground()
    moreButton.isVisible = false
    add(topStripe, BorderLayout.CENTER)
    topStripe.addButtonAddedRemovedListener { updateVisibleButtons() }
    updateVisibleButtons()
  }

  override fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe = topStripe

  override fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    // Do NOT gate on isShowing here: an empty horizontal bar is invisible, but we must still allow dropping the first tool
    // window onto it. topStripe.containsPoint has a hidden-bar fallback (it measures against the always-showing content pane).
    return if (topStripe.containsPoint(screenPoint)) topStripe else null
  }

  override fun createBorder(): Border {
    return if (anchor == ToolWindowAnchor.TOP) JBUI.Borders.customLineBottom(getBorderColor())
    else JBUI.Borders.customLineTop(getBorderColor())
  }
}

/**
 * The single stripe of a horizontal (TOP/BOTTOM) bar. Unlike the vertical [ToolWindowToolbar.StripeV2], its drop band must
 * cover both the bar itself (so a cursor over the bar's own buttons is recognized) and the docking-preview depth in the
 * content area. When the bar is empty/hidden it falls back to the content pane's near edge so the first drop still works.
 */
private class HorizontalStripe(toolBar: ToolWindowToolbar, paneId: String, anchor: ToolWindowAnchor) :
  ToolWindowToolbar.StripeV2(toolBar, paneId, anchor) {

  override fun isHorizontal(): Boolean = true

  override fun containsPoint(screenPoint: Point): Boolean {
    val depth = max(getFirstVisibleToolWindowSize(false), JBUI.scale(40))
    val bounds: Rectangle

    if (toolBar.isShowing) {
      // The bar is the NORTH/SOUTH sibling of the content pane, so extend the toolbar's own bounds by depth into the content.
      bounds = Rectangle(toolBar.locationOnScreen, toolBar.size)
      if (anchor == ToolWindowAnchor.BOTTOM) {
        bounds.y -= depth
      }
      bounds.height += depth
    }
    else {
      val pane = bottomAnchorDropAreaComponent ?: rootPane
      bounds = Rectangle(pane.locationOnScreen, pane.size)
      if (anchor == ToolWindowAnchor.BOTTOM) {
        bounds.y += bounds.height - depth - getStatusBarHeight()
      }
      bounds.height = depth
    }

    return bounds.contains(screenPoint)
  }

  override fun getToolWindowDropAreaScreenBounds(): Rectangle {
    val pane = bottomAnchorDropAreaComponent ?: rootPane
    val bounds = Rectangle(pane.locationOnScreen, pane.size)
    val toolWindowHeight = getFirstVisibleToolWindowSize(false)
    if (anchor == ToolWindowAnchor.BOTTOM) {
      bounds.y += bounds.height - toolWindowHeight
    }
    bounds.height = toolWindowHeight
    return bounds
  }
}
