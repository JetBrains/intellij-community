// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.codeInsight.daemon.impl.HintRenderer.Companion.BACKGROUND_ALPHA
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComponent

private val BACKGROUND = JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D))

@ApiStatus.NonExtendable
abstract class AbstractFloatingToolbarComponent : ActionToolbarImpl, FloatingToolbarComponent, Disposable.Default {
  private val _parentDisposable: Disposable?
  private val parentDisposable: Disposable
    get() = _parentDisposable ?: this

  private val transparentComponent = ToolbarTransparentComponent()
  private val componentAnimator = TransparentComponentAnimator(transparentComponent, parentDisposable)

  constructor(
    actionGroup: ActionGroup,
    parentDisposable: Disposable
  ) : super(ActionPlaces.CONTEXT_TOOLBAR, actionGroup, true) {
    this._parentDisposable = parentDisposable
  }

  @ApiStatus.Internal
  var backgroundAlpha: Float = BACKGROUND_ALPHA

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var showingTime: Int by componentAnimator::showingTime

  @get:ApiStatus.Internal
  @set:ApiStatus.Internal
  var hidingTime: Int by componentAnimator::hidingTime

  protected abstract val autoHideable: Boolean

  protected abstract fun isComponentOnHold(): Boolean

  protected abstract fun installMouseMotionWatcher()

  protected fun init(targetComponent: JComponent) {
    setTargetComponent(targetComponent)
    minimumButtonSize = Dimension(22, 22)
    setSkipWindowAdjustments(true)
    isReservePlaceAutoPopupIcon = false
    isOpaque = false
    layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY

    transparentComponent.hideComponent()

    installMouseMotionWatcher()

    if (_parentDisposable != null) {
      Disposer.register(_parentDisposable, this)
    }
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

    override val autoHideable: Boolean
      get() = toolbar.autoHideable

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