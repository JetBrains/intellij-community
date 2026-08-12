// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.extendedToolWindowsUi

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.islands.isIslandTheme
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.toolWindow.MoreSquareStripeButton
import com.intellij.toolWindow.ToolWindowToolbar
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.ui.border.CustomLineBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import javax.swing.border.Border
import kotlin.math.max

/**
 * Keep this class outside the plugin to avoid a massive move from internal to [ApiStatus.Internal]
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

  private val bottomStripeBorderHelper = if (anchor == ToolWindowAnchor.BOTTOM) BottomStripeBorderHelper(this) else null

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

  override fun getStripeFor(screenPoint: Point): StripeV2? {
    // Do NOT gate on isShowing here: an empty horizontal bar is invisible, but we must still allow dropping the first tool
    // window onto it. topStripe.containsPoint has a hidden-bar fallback (it measures against the always-showing content pane).
    return if (topStripe.containsPoint(screenPoint)) topStripe else null
  }

  override fun createBorder(): Border {
    if (bottomStripeBorderHelper != null) {
      return bottomStripeBorderHelper.createBorder()
    }

    // Top horizontal stripe
    return JBUI.Borders.customLineTop(getBorderColor())
  }

  override fun getBorderColor(): Color {
    return JBColor.namedColor("ColorPalette.main-window-border",
                              JBUI.CurrentTheme.ToolWindow.stripeSeparatorColor(false))
  }
}

private class BottomStripeBorderHelper(private val toolBar: ToolWindowHorizontalToolbar) {

  init {
    toolBar.launchOnShow("BottomStripeBorderHelper") {
      updateBorder()

      ApplicationManager.getApplication().messageBus.connect(this)
        .subscribe(UISettingsListener.TOPIC, UISettingsListener {
          updateBorder()
        })

      // Keep the coroutine for the subscription
      awaitCancellation()
    }
  }

  fun createBorder(): Border {
    return JBUI.Borders.customLine(toolBar.getBorderColor(),
                                   if (isTopLineNeeded()) 1 else 0, 0,
                                   if (isBottomLineNeeded()) 1 else 0, 0)
  }

  private fun islandsLikeTheme(): Boolean {
    return LafManager.getInstance().currentUIThemeLookAndFeel?.id == "JetBrainsHighContrastTheme" || isIslandTheme()
  }

  private fun isTopLineNeeded(): Boolean {
    return !islandsLikeTheme()
  }

  private fun isBottomLineNeeded(): Boolean {
    return islandsLikeTheme() && UISettings.getInstance().showStatusBar
  }

  private fun updateBorder() {
    val insets = (toolBar.border as? CustomLineBorder)?.getBorderInsets(toolBar)
    if (insets != null &&
        (insets.top > 0 == isTopLineNeeded()) &&
        (insets.bottom > 0 == isBottomLineNeeded())) {
      return
    }
    toolBar.border = createBorder()
    toolBar.revalidate()
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
    // The bar itself (full width) is a drop target, so a cursor over the bar's own buttons is recognized.
    if (toolBar.isShowing && Rectangle(toolBar.locationOnScreen, toolBar.size).contains(screenPoint)) {
      return true
    }

    val depth = max(getFirstVisibleToolWindowSize(false), JBUI.scale(40))
    val pane = bottomAnchorDropAreaComponent ?: rootPane
    val bounds = Rectangle(pane.locationOnScreen, pane.size)
    if (anchor == ToolWindowAnchor.BOTTOM) {
      bounds.y += bounds.height - depth - getStatusBarHeight()
    }
    bounds.height = depth
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
