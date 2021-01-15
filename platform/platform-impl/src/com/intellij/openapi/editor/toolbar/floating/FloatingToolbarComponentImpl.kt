// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class FloatingToolbarComponentImpl(
  parentComponent: JComponent,
  contextComponent: JComponent,
  actionGroup: ActionGroup,
  val autoHideable: Boolean,
  parentDisposable: Disposable
) : JPanel(), FloatingToolbarComponent {

  private val actionToolbar: ActionToolbar
  private val visibilityController: VisibilityController

  @Deprecated("see FloatingToolbarComponent#update")
  override fun update() = actionToolbar.updateActionsImmediately()

  override fun scheduleShow() = invokeLater {
    actionToolbar.updateActionsImmediately()
    visibilityController.scheduleShow()
  }

  override fun scheduleHide() = invokeLater {
    actionToolbar.updateActionsImmediately()
    visibilityController.scheduleHide()
  }

  override fun paintComponent(g: Graphics) {
    val graphics = g.create()
    try {
      if (graphics is Graphics2D) {
        val alpha = visibilityController.opacity * BACKGROUND_ALPHA
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
        val alpha = visibilityController.opacity
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha)
      }
      super.paintChildren(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  init {
    layout = BorderLayout(0, 0)
    border = BorderFactory.createEmptyBorder()
    isOpaque = false
    isVisible = false

    actionToolbar = ActionToolbarImpl(ActionPlaces.CONTEXT_TOOLBAR, actionGroup, true)
    actionToolbar.setTargetComponent(contextComponent)
    actionToolbar.setMinimumButtonSize(Dimension(22, 22))
    actionToolbar.setSkipWindowAdjustments(true)
    actionToolbar.setReservePlaceAutoPopupIcon(false)
    actionToolbar.isOpaque = false
    add(actionToolbar, BorderLayout.CENTER)

    visibilityController = ToolbarVisibilityController(autoHideable, parentComponent, actionToolbar, this)
    visibilityController.scheduleHide()

    Disposer.register(parentDisposable, visibilityController)
  }

  companion object {
    private const val BACKGROUND_ALPHA = 0.75f
    private val BACKGROUND = JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D))
  }
}