// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.toolWindow

import com.intellij.openapi.actionSystem.ActionPlaces.TOOLWINDOW_TOOLBAR_BAR
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.LayoutData
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.Border

internal abstract class ToolWindowToolbar : JPanel() {
  lateinit var defaults: List<String>

  abstract val bottomStripe: StripeV2
  abstract val topStripe: StripeV2

  protected fun init() {
    layout = BorderLayout()
    isOpaque = true
    background = JBUI.CurrentTheme.ToolWindow.background()

    val topWrapper = JPanel(BorderLayout())
    border = createBorder()
    topStripe.background = JBUI.CurrentTheme.ToolWindow.background()
    bottomStripe.background = JBUI.CurrentTheme.ToolWindow.background()
    topWrapper.background = JBUI.CurrentTheme.ToolWindow.background()

    topWrapper.add(topStripe, BorderLayout.NORTH)
    add(topWrapper, BorderLayout.NORTH)
    add(bottomStripe, BorderLayout.SOUTH)
  }

  open fun createBorder():Border = JBUI.Borders.empty()
  open fun getBorderColor(): Color? = JBUI.CurrentTheme.ToolWindow.borderColor()

  abstract fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe

  fun getButtonFor(toolWindowId: String): StripeButtonManager? {
    return topStripe.getButtons().find { it.id == toolWindowId } ?: bottomStripe.getButtons().find { it.id == toolWindowId }
  }

  open fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isShowing) {
      return null
    }
    val topRect = Rectangle(locationOnScreen, size).also {
      it.width = it.width.coerceAtLeast(SHADOW_WIDTH)
      it.height = (it.height / 2).coerceAtLeast(SHADOW_WIDTH)
    }
    val bottomRect = Rectangle(topRect).also {
      it.y += topRect.height
    }
    return if (topRect.contains(screenPoint)) {
      topStripe
    }
    else if (bottomRect.contains(screenPoint)) {
      bottomStripe
    }
    else {
      null
    }
  }

  fun removeStripeButton(toolWindow: ToolWindow, anchor: ToolWindowAnchor) {
    remove(getStripeFor(anchor), toolWindow)
  }

  fun hasButtons() = topStripe.getButtons().isNotEmpty() || bottomStripe.getButtons().isNotEmpty()

  fun reset() {
    topStripe.reset()
    bottomStripe.reset()
  }

  fun startDrag() {
    revalidate()
    repaint()
  }

  fun stopDrag() = startDrag()

  fun tryDroppingOnGap(data: LayoutData, gap: Int, dropRectangle: Rectangle, doLayout: () -> Unit) {
    val sideDistance = data.eachY + gap - dropRectangle.y + dropRectangle.height
    if (sideDistance > 0) {
      data.dragInsertPosition = -1
      data.dragToSide = false
      data.dragTargetChosen = true
      doLayout()
    }
  }

  companion object {
    val SHADOW_WIDTH = JBUI.scale(40)

    fun updateButtons(panel: JComponent) {
      ComponentUtil.findComponentsOfType(panel, SquareStripeButton::class.java).forEach { it.update() }
      panel.revalidate()
      panel.repaint()
    }

    fun remove(panel: AbstractDroppableStripe, toolWindow: ToolWindow) {
      val component = panel.components.firstOrNull { it is SquareStripeButton && it.toolWindow.id == toolWindow.id } ?: return
      panel.remove(component)
      panel.revalidate()
      panel.repaint()
    }
  }

  open class ToolwindowActionToolbar(val panel: JComponent) : ActionToolbarImpl(TOOLWINDOW_TOOLBAR_BAR, DefaultActionGroup(), false) {
    override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) = updateButtons(panel)
  }

  internal class StripeV2(private val toolBar: ToolWindowToolbar,
                          paneId: String,
                          override val anchor: ToolWindowAnchor,
                          override val split: Boolean = false) : AbstractDroppableStripe(paneId, VerticalFlowLayout(0, 0)) {
    override val isNewStripes: Boolean
      get() = true

    override fun getButtonFor(toolWindowId: String) = toolBar.getButtonFor(toolWindowId)

    override fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) {
      toolBar.tryDroppingOnGap(data, gap, dropRectangle) {
        layoutDragButton(data, gap)
      }
    }

    override fun toString() = "StripeNewUi(anchor=$anchor)"
  }
}