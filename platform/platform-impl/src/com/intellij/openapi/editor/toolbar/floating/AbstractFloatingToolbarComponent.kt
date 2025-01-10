// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.codeInsight.daemon.impl.HintRenderer.Companion.BACKGROUND_ALPHA
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComponent

private val BACKGROUND = JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D))

@ApiStatus.NonExtendable
abstract class AbstractFloatingToolbarComponent(
  actionGroup: ActionGroup,
  ownerComponent: JComponent,
  parentDisposable: Disposable,
) : ActionToolbarImpl(ActionPlaces.CONTEXT_TOOLBAR, actionGroup, true),
    FloatingToolbarComponent {

  private val transparentComponent = ToolbarTransparentComponent()
  private val componentAnimator = TransparentComponentAnimator(transparentComponent, parentDisposable)

  override var backgroundAlpha: Float = BACKGROUND_ALPHA

  override var showingTime: Int by componentAnimator::showingTime

  override var hidingTime: Int by componentAnimator::hidingTime

  override var retentionTime: Int by componentAnimator::retentionTime

  override var autoHideable: Boolean by componentAnimator::autoHideable

  protected open fun isComponentOnHold(): Boolean = false

  init {
    targetComponent = ownerComponent
    minimumButtonSize = Dimension(22, 22)
    setSkipWindowAdjustments(true)
    isReservePlaceAutoPopupIcon = false
    isOpaque = false
    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
  }

  override fun addNotify() {
    super.addNotify()
    updateActionsImmediately(true)
  }

  override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
    super.actionsUpdated(forced, newVisibleActions)
    transparentComponent.fireActionsUpdated()
  }

  override fun scheduleShow(): Unit = componentAnimator.scheduleShow()

  override fun scheduleHide(): Unit = componentAnimator.scheduleHide()

  override fun hideImmediately(): Unit = componentAnimator.hideImmediately()

  override fun paintComponent(g: Graphics) {
    val graphics = g.create()
    try {
      if (graphics is Graphics2D) {
        val opacity = transparentComponent.getOpacity() * backgroundAlpha
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
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
        val opacity = transparentComponent.getOpacity()
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
      }
      super.paintChildren(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  private inner class ToolbarTransparentComponent : TransparentComponent {
    private val toolbar = this@AbstractFloatingToolbarComponent

    private var isVisible = false
    private var opacity: Float = 0.0f

    init {
      toolbar.putClientProperty(SUPPRESS_FAST_TRACK, true)
    }

    fun getOpacity(): Float = opacity

    override fun setOpacity(opacity: Float) {
      this.opacity = opacity
    }

    override fun isComponentOnHold(): Boolean = toolbar.isComponentOnHold()

    override fun showComponent() {
      isVisible = true
      toolbar.updateActionsImmediately(true)
    }

    override fun hideComponent() {
      if (!isVisible) return
      isVisible = false
      toolbar.updateActionsImmediately(false)
    }

    override fun repaintComponent() = toolbar.component.repaint()

    fun fireActionsUpdated() {
      toolbar.isVisible = isVisible && toolbar.hasVisibleActions()
    }
  }
}