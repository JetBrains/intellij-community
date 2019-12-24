// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.actionSystem.ActionToolbar
import java.awt.MouseInfo
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities


class ToolbarVisibilityController(
  override val autoHide: Boolean,
  private val parentComponent: JComponent,
  private val actionToolbar: ActionToolbar,
  private val toolbarComponent: JComponent
) : VisibilityController() {

  override fun setVisible(isVisible: Boolean) {
    toolbarComponent.isVisible = actionToolbar.hasVisibleActions() && isVisible
  }

  override fun repaint() {
    toolbarComponent.repaint()
  }

  override fun isRetention(): Boolean {
    val pointerInfo = MouseInfo.getPointerInfo() ?: return false
    val location = pointerInfo.location
    SwingUtilities.convertPointFromScreen(location, parentComponent)
    val bounds = Rectangle(0, 0, parentComponent.width, parentComponent.height)
    return bounds.contains(location)
  }
}