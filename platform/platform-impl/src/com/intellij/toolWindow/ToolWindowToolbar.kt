// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.toolWindow

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.LayoutData
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.ui.ComponentUtil
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.Rectangle
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.border.Border
import kotlin.math.max

@ApiStatus.Internal
abstract class ToolWindowToolbar(private val isPrimary: Boolean, val anchor: ToolWindowAnchor) : JBPanel<ToolWindowToolbar>() {
  lateinit var defaults: List<String>

  internal abstract val bottomStripe: StripeV2
  internal abstract val topStripe: StripeV2

  internal abstract val moreButton: MoreSquareStripeButton

  private val myResizeManager = ResizeStripeManager(this)

  protected fun init() {
    layout = myResizeManager.createLayout()
    isOpaque = true
    background = JBUI.CurrentTheme.ToolWindow.background()

    val topWrapper = JBPanel<JBPanel<*>>(BorderLayout()).apply {
      border = JBUI.Borders.customLineTop(getBorderColor())
    }
    border = createBorder()
    topStripe.background = JBUI.CurrentTheme.ToolWindow.background()
    bottomStripe.background = JBUI.CurrentTheme.ToolWindow.background()
    topWrapper.background = JBUI.CurrentTheme.ToolWindow.background()

    topWrapper.add(topStripe, BorderLayout.NORTH)
    add(topWrapper, BorderLayout.NORTH)
    add(bottomStripe, BorderLayout.SOUTH)
  }

  fun initMoreButton(project: Project) {
    if (isPrimary) {
      topStripe.parent?.add(moreButton, BorderLayout.CENTER)
      moreButton.updateState(project)
    }
  }

  fun updateResizeState(toolbar: ToolWindowToolbar?) {
    myResizeManager.updateState(toolbar)
  }

  fun updateNamedState() {
    if (isVisible && ResizeStripeManager.isShowNames()) {
      myResizeManager.updateNamedState()
    }
  }

  open fun createBorder():Border = JBUI.Borders.empty()
  open fun getBorderColor(): Color? = JBUI.CurrentTheme.ToolWindow.borderColor()

  internal abstract fun getStripeFor(anchor: ToolWindowAnchor): AbstractDroppableStripe

  internal fun getButtonFor(toolWindowId: String): StripeButtonManager? {
    return topStripe.getButtons().find { it.id == toolWindowId } ?: bottomStripe.getButtons().find { it.id == toolWindowId }
  }

  internal open fun getStripeFor(screenPoint: Point): AbstractDroppableStripe? {
    if (!isShowing) {
      return null
    }
    if (topStripe.containsPoint(screenPoint)) {
      return topStripe
    }
    if (bottomStripe.containsPoint(screenPoint)) {
      return bottomStripe
    }
    return null
  }

  fun hasButtons(): Boolean = topStripe.getButtons().isNotEmpty() || bottomStripe.getButtons().isNotEmpty()

  fun reset() {
    topStripe.reset()
    bottomStripe.reset()
  }

  fun startDrag() {
    revalidate()
    repaint()
  }

  fun stopDrag(): Unit = startDrag()

  internal fun tryDroppingOnGap(data: LayoutData, gap: Int, dropRectangle: Rectangle, doLayout: () -> Unit) {
    val sideDistance = data.eachY + gap - dropRectangle.y + dropRectangle.height
    if (sideDistance > 0) {
      data.dragInsertPosition = -1
      data.dragToSide = false
      data.dragTargetChosen = true
      doLayout()
    }
  }

  companion object {
    fun updateButtons(panel: JComponent) {
      ComponentUtil.findComponentsOfType(panel, SquareStripeButton::class.java).forEach { it.update() }
      panel.revalidate()
      panel.repaint()
    }

    internal fun remove(panel: AbstractDroppableStripe, toolWindow: ToolWindow) {
      val component = panel.components.firstOrNull { it is SquareStripeButton && it.toolWindow.id == toolWindow.id } ?: return
      panel.remove(component)
      panel.revalidate()
      panel.repaint()
    }
  }

  internal class StripeV2(private val toolBar: ToolWindowToolbar,
                          paneId: String,
                          override val anchor: ToolWindowAnchor,
                          override val split: Boolean = false) : AbstractDroppableStripe(paneId, VerticalFlowLayout(0, 0)) {
    var bottomAnchorDropAreaComponent: JComponent? = null
    override val isNewStripes: Boolean
      get() = true

    override fun getDropToSide(): Boolean {
      if (split) {
        return true
      }
      val dropToSide = super.getDropToSide()
      if (dropToSide == null) {
        return false
      }
      return dropToSide
    }

    override fun containsPoint(screenPoint: Point): Boolean {
      if (anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT) {
        if (!toolBar.isShowing) {
          val bounds = Rectangle(rootPane.locationOnScreen, rootPane.size)
          bounds.height /= 2

          val toolWindowWidth = getFirstVisibleToolWindowSize(true)

          if (anchor == ToolWindowAnchor.RIGHT) {
            bounds.x = bounds.x + bounds.width - toolWindowWidth
          }

          bounds.width = toolWindowWidth

          return bounds.contains(screenPoint)
        }

        val bounds = Rectangle(toolBar.locationOnScreen, toolBar.size)
        bounds.height /= 2

        val toolWindowWidth = getFirstVisibleToolWindowSize(true)

        bounds.width += toolWindowWidth
        if (anchor == ToolWindowAnchor.RIGHT) {
          bounds.x -= toolWindowWidth
        }
        return bounds.contains(screenPoint)
      }
      if (anchor == ToolWindowAnchor.BOTTOM) {
        val rootBounds = Rectangle(rootPane.locationOnScreen, rootPane.size)
        val toolWindowHeight = max(getFirstVisibleToolWindowSize(false), height + JBUI.scale(40))
        val bounds = Rectangle(rootBounds.x, rootBounds.y + rootBounds.height - toolWindowHeight - getStatusBarHeight(),
                               rootBounds.width / 2, toolWindowHeight)
        if (split) {
          bounds.x += bounds.width
        }
        return bounds.contains(screenPoint)
      }
      return super.containsPoint(screenPoint)
    }

    private fun getFirstVisibleToolWindowSize(width: Boolean): Int {
      for (button in getButtons()) {
        if (button.toolWindow.isVisible) {
          if (width) {
            return (rootPane.width * button.windowDescriptor.weight).toInt()
          }
          return (rootPane.height * button.windowDescriptor.weight).toInt()
        }
      }

      return JBUI.scale(350)
    }

    private fun getStatusBarHeight(): Int {
      val statusBar = WindowManager.getInstance().getStatusBar(this, null)
      if (statusBar != null) {
        val component = statusBar.component
        if (component != null && component.isVisible) {
          return component.height
        }
      }
      return 0
    }

    override fun getToolWindowDropAreaScreenBounds(): Rectangle {
      val size = toolBar.size

      if (anchor == ToolWindowAnchor.LEFT) {
        val locationOnScreen = toolBar.locationOnScreen
        return Rectangle(locationOnScreen.x + size.width, locationOnScreen.y, getFirstVisibleToolWindowSize(true), size.height)
      }
      if (anchor == ToolWindowAnchor.RIGHT) {
        val locationOnScreen = toolBar.locationOnScreen
        val toolWindowSize = getFirstVisibleToolWindowSize(true)
        return Rectangle(locationOnScreen.x  - toolWindowSize, locationOnScreen.y, toolWindowSize, size.height)
      }
      if (anchor == ToolWindowAnchor.BOTTOM) {
        val pane = bottomAnchorDropAreaComponent ?: rootPane
        val rootBounds = Rectangle(pane.locationOnScreen, pane.size)
        val toolWindowHeight = getFirstVisibleToolWindowSize(false)
        return Rectangle(rootBounds.x, rootBounds.y + rootBounds.height - toolWindowHeight, rootBounds.width, toolWindowHeight)
      }
      return super.getToolWindowDropAreaScreenBounds()
    }

    override fun getButtonFor(toolWindowId: String): StripeButtonManager? = toolBar.getButtonFor(toolWindowId)

    override fun tryDroppingOnGap(data: LayoutData, gap: Int, insertOrder: Int) {
      toolBar.tryDroppingOnGap(data, gap, dropRectangle) {
        layoutDragButton(data, gap)
      }
    }

    override fun toString(): String = "StripeNewUi(anchor=$anchor)"
  }

  protected open val accessibleGroupName: @NlsSafe String get() = UIBundle.message("toolbar.group.default.accessible.group.name")

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) accessibleContext = AccessibleToolWindowToolbar()
    accessibleContext.accessibleName = accessibleGroupName

    return accessibleContext
  }

  private inner class AccessibleToolWindowToolbar : AccessibleJPanel() {
    override fun getAccessibleRole(): AccessibleRole = AccessibilityUtils.GROUPED_ELEMENTS
  }
}