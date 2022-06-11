// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Rectangle
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane

private const val GAP_FOR_BUTTONS = 80

internal class MacToolbarFrameHeader(private val frame: JFrame,
                                     private val root: JRootPane,
                                     private val ideMenu: IdeMenuBar) : CustomHeader(frame), MainFrameCustomHeader, ToolbarHolder {

  private var myToolbar : MainToolbar? = null

  init {
    layout = BorderLayout()
    root.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })
  }

  override fun updateToolbar() {
    removeToolbar()

    val toolbar = MainToolbar()
    toolbar.init((frame as? IdeFrame)?.project)
    toolbar.isOpaque = false
    myToolbar = toolbar

    add(myToolbar, BorderLayout.CENTER)
    revalidate()
    updateCustomDecorationHitTestSpots()
  }

  override fun removeToolbar() {
    removeAll()
    revalidate()
  }

  override fun windowStateChanged() {
    super.windowStateChanged()
    updateBorders()
  }

  override fun addNotify() {
    super.addNotify()
    updateBorders()
  }

  override fun createButtonsPane(): CustomFrameTitleButtons = CustomFrameTitleButtons.create(myCloseAction)

  override fun getHitTestSpots(): List<Pair<RelativeRectangle, Int>> {
    return myToolbar
      ?.components
      ?.filter { it.isVisible }
      ?.map { Pair(getElementRect(it), CustomWindowDecoration.MENU_BAR) }
      ?.toList() ?: emptyList()
  }

  override fun updateMenuActions(forceRebuild: Boolean) = ideMenu.updateMenuActions(forceRebuild)

  override fun getComponent(): JComponent = this

  override fun dispose() {}

  private fun getElementRect(comp: Component): RelativeRectangle {
    val rect = Rectangle(comp.size)
    return RelativeRectangle(comp, rect)
  }

  private fun updateBorders() {
    val isFullscreen = root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null
    border = if (isFullscreen) JBUI.Borders.empty() else JBUI.Borders.emptyLeft(GAP_FOR_BUTTONS)
  }
}