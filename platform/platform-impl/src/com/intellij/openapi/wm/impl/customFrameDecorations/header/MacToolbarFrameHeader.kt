// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.util.ui.JBUI
import com.jetbrains.CustomWindowDecoration
import com.jetbrains.JBR
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane

private const val GAP_FOR_BUTTONS = 80
private const val DEFAULT_HEADER_HEIGHT = 40

internal class MacToolbarFrameHeader(private val frame: JFrame,
                                     private val root: JRootPane,
                                     private val ideMenu: IdeMenuBar) : CustomHeader(frame), MainFrameCustomHeader, ToolbarHolder {
  private var toolbar: MainToolbar?

  private val LEFT_GAP = JBUI.scale(16)
  private val RIGHT_GAP = JBUI.scale(24)

  init {
    layout = BorderLayout()
    root.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })
    add(ideMenu, BorderLayout.NORTH)

    toolbar = createToolBar()
  }

  private fun createToolBar(): MainToolbar {
    val toolbar = MainToolbar()
    toolbar.isOpaque = false
    toolbar.border = JBUI.Borders.empty(0, LEFT_GAP, 0, RIGHT_GAP)
    add(toolbar, BorderLayout.CENTER)
    return toolbar
  }

  override fun initToolbar() {
    var tb = toolbar
    if (tb == null) {
      tb = createToolBar()
      toolbar = tb
    }
    tb.init((frame as? IdeFrame)?.project)
  }

  override fun updateToolbar() {
    removeToolbar()

    val toolbar = createToolBar()
    this.toolbar = toolbar
    toolbar.init((frame as? IdeFrame)?.project)

    revalidate()
    updateCustomDecorationHitTestSpots()
  }

  override fun removeToolbar() {
    val toolbar = toolbar ?: return
    this.toolbar = null
    remove(toolbar)
    revalidate()
  }

  override fun windowStateChanged() {
    super.windowStateChanged()
    updateBorders()
  }

  override fun addNotify() {
    super.addNotify()
    updateBorders()

    val decor = JBR.getCustomWindowDecoration()
    decor.setCustomDecorationTitleBarHeight(frame, DEFAULT_HEADER_HEIGHT)
  }

  override fun createButtonsPane(): CustomFrameTitleButtons = CustomFrameTitleButtons.create(myCloseAction)

  override fun getHitTestSpots(): Sequence<Pair<RelativeRectangle, Int>> {
    return (toolbar ?: return emptySequence())
      .components
      .asSequence()
      .filter { it.isVisible }
      .map { Pair(getElementRect(it), CustomWindowDecoration.MENU_BAR) }
  }

  override fun updateMenuActions(forceRebuild: Boolean) = ideMenu.updateMenuActions(forceRebuild)

  override fun getComponent(): JComponent = this

  override fun dispose() {}

  private fun getElementRect(comp: Component): RelativeRectangle {
    val rect = Rectangle(comp.size)
    return RelativeRectangle(comp, rect)
  }

  override fun getHeaderBackground(active: Boolean) = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(active)

  private fun updateBorders() {
    val isFullscreen = root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null
    border = if (isFullscreen) JBUI.Borders.empty() else JBUI.Borders.emptyLeft(GAP_FOR_BUTTONS)
  }

  override fun updateActive() {
    super.updateActive()
    toolbar?.background = getHeaderBackground(myActive)
  }
}