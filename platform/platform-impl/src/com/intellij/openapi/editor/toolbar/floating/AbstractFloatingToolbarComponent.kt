// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComponent

@ApiStatus.NonExtendable
abstract class AbstractFloatingToolbarComponent : ActionToolbarImpl, FloatingToolbarComponent, Disposable.Default {

  private val _parentDisposable: Disposable?
  private val parentDisposable get() = _parentDisposable ?: this

  private val transparentComponent by lazy { ToolbarTransparentComponent(this) }
  private val componentAnimator by lazy { TransparentComponentAnimator(transparentComponent, parentDisposable) }

  @Deprecated("Use constructor with parentDisposable")
  constructor(
    actionGroup: ActionGroup
  ) : super(ActionPlaces.CONTEXT_TOOLBAR, actionGroup, true) {
    this._parentDisposable = null
  }

  constructor(
    actionGroup: ActionGroup,
    parentDisposable: Disposable
  ) : super(ActionPlaces.CONTEXT_TOOLBAR, actionGroup, true) {
    this._parentDisposable = parentDisposable
  }

  protected abstract val autoHideable: Boolean

  protected abstract fun isComponentOnHold(): Boolean

  protected abstract fun installMouseMotionWatcher()

  protected fun init(targetComponent: JComponent) {
    setTargetComponent(targetComponent)
    setMinimumButtonSize(Dimension(22, 22))
    setSkipWindowAdjustments(true)
    setReservePlaceAutoPopupIcon(false)
    isOpaque = false
    layoutPolicy = NOWRAP_LAYOUT_POLICY

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

  override fun scheduleShow() = componentAnimator.scheduleShow()

  override fun scheduleHide() = componentAnimator.scheduleHide()

  override fun hideImmediately() = componentAnimator.hideImmediately()

  override fun paintComponent(g: Graphics) {
    val graphics = g.create()
    try {
      if (graphics is Graphics2D) {
        val opacity = transparentComponent.getOpacity() * BACKGROUND_ALPHA
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

  companion object {
    private const val BACKGROUND_ALPHA = 0.75f
    private val BACKGROUND = JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D))
  }

  private class ToolbarTransparentComponent(
    private val toolbar: AbstractFloatingToolbarComponent
  ) : TransparentComponent {

    private var isVisible = false
    private var opacity: Float = 0.0f

    fun getOpacity(): Float {
      return opacity
    }

    override fun setOpacity(opacity: Float) {
      this.opacity = opacity
    }

    override val autoHideable: Boolean
      get() = toolbar.autoHideable

    override fun isComponentOnHold(): Boolean {
      return toolbar.isComponentOnHold()
    }

    override fun showComponent() {
      isVisible = true
      toolbar.updateActionsImmediately(true)
    }

    override fun hideComponent() {
      isVisible = false
      toolbar.updateActionsImmediately(false)
    }

    override fun repaintComponent() {
      return toolbar.component.repaint()
    }

    fun fireActionsUpdated() {
      toolbar.isVisible = isVisible && toolbar.hasVisibleActions()
    }

    init {
      toolbar.putClientProperty(SUPPRESS_FAST_TRACK, true)
    }
  }
}