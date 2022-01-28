// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor


import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.toolbar.floating.ToolbarVisibilityController
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.JComponent

class LayoutActionsFloatingToolbar(
  parentComponent: JComponent,
  actionGroup: ActionGroup
) : ActionToolbarImpl(ActionPlaces.CONTEXT_TOOLBAR, actionGroup, true), Disposable {
  val visibilityController = ToolbarVisibilityController(false, parentComponent, this, this)

  init {
    Disposer.register(this, visibilityController)
    targetComponent = parentComponent
    setReservePlaceAutoPopupIcon(false)
    setMinimumButtonSize(Dimension(22, 22))
    setSkipWindowAdjustments(true)
    isOpaque = false
    layoutPolicy = NOWRAP_LAYOUT_POLICY
  }
  override fun paintComponent(g: Graphics) {
    val graphics = g.create()
    try {
      if (graphics is Graphics2D) {
        val alpha = visibilityController.opacity * BACKGROUND_ALPHA
        if (alpha == 0.0f) {
          updateActionsImmediately()
        }
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      }
      graphics.color = BACKGROUND
      graphics.fillRoundRect(0, 0, bounds.width, bounds.height, 6, 6)

      super.paintComponent(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  override fun paintChildren(g: Graphics) {
    val graphics = g.create()
    try {
      if (graphics is Graphics2D) {
        val alpha = visibilityController.opacity * BACKGROUND_ALPHA
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
      }
      super.paintChildren(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  override fun addNotify() {
    super.addNotify()
    updateActionsImmediately(true)
  }

  override fun dispose() = Unit

  companion object {
    private const val BACKGROUND_ALPHA = 0.75f
    private val BACKGROUND = JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D))
  }
}
