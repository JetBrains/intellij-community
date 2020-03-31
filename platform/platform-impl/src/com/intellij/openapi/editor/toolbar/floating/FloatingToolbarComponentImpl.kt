// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import java.awt.*
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel

class FloatingToolbarComponentImpl(
  parentComponent: JComponent,
  private val contextComponent: JComponent,
  actionGroup: ActionGroup,
  autoHideable: Boolean,
  parentDisposable: Disposable
) : JPanel(), FloatingToolbarComponent, DataProvider {

  private val actionToolbar: ActionToolbar
  private val visibilityController: VisibilityController

  override fun update() = actionToolbar.updateActionsImmediately()
  override fun scheduleShow() = visibilityController.scheduleShow()
  override fun scheduleHide() = visibilityController.scheduleHide()

  override fun getData(dataId: String): Any? {
    if (FloatingToolbarComponent.KEY.`is`(dataId)) return this
    val dataManager = DataManager.getInstance()
    val dataContext = dataManager.getDataContext(contextComponent)
    return dataContext.getData(dataId)
  }

  override fun paintChildren(g: Graphics) {
    val graphics = g.create() as Graphics2D
    try {
      val alpha = visibilityController.opacity * FOREGROUND_ALPHA
      graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, alpha)
      super.paintChildren(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  override fun paint(g: Graphics) {
    paintComponent(g)
    super.paint(g)
  }

  override fun paintComponent(g: Graphics) {
    val r = bounds
    val graphics = g.create() as Graphics2D
    try {
      val alpha = visibilityController.opacity * BACKGROUND_ALPHA
      graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      graphics.color = BACKGROUND
      graphics.fillRoundRect(0, 0, r.width, r.height, 6, 6)
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
    actionToolbar.setTargetComponent(this)
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
    val BACKGROUND = JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D))
    private const val BACKGROUND_ALPHA = 0.9f
    private const val FOREGROUND_ALPHA = 1.0f
  }
}