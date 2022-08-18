// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.JComponent

@ApiStatus.Internal
abstract class AbstractFloatingToolbarComponent(
  actionGroup: ActionGroup,
  override val autoHideable: Boolean,
) : ActionToolbarImpl(ActionPlaces.CONTEXT_TOOLBAR, actionGroup, true),
    FloatingToolbarComponent,
    TransparentComponent,
    Disposable {

  private val componentAnimator by lazy { TransparentComponentAnimator(this, this) }

  override val component: Component get() = getComponent()

  override var opacity: Float = 0.0f

  override fun showComponent() {
    updateActionsImmediately(true)
    isVisible = hasVisibleActions()
  }

  override fun hideComponent() {
    updateActionsImmediately()
    isVisible = false
  }

  override fun addNotify() {
    super.addNotify()
    updateActionsImmediately(true)
  }

  override fun scheduleShow() = componentAnimator.scheduleShow()

  override fun scheduleHide() = componentAnimator.scheduleHide()

  protected fun hideImmediately() = componentAnimator.hideImmediately()

  override fun paintComponent(g: Graphics) {
    val graphics = g.create()
    try {
      if (graphics is Graphics2D) {
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity * BACKGROUND_ALPHA)
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
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
      }
      super.paintChildren(graphics)
    }
    finally {
      graphics.dispose()
    }
  }

  fun init(targetComponent: JComponent) {
    setTargetComponent(targetComponent)
    setMinimumButtonSize(Dimension(22, 22))
    setSkipWindowAdjustments(true)
    setReservePlaceAutoPopupIcon(false)
    isOpaque = false
    layoutPolicy = NOWRAP_LAYOUT_POLICY
  }

  override fun dispose() = Unit

  init {
    componentAnimator.scheduleHide()
  }

  companion object {
    private const val BACKGROUND_ALPHA = 0.75f
    private val BACKGROUND = JBColor.namedColor("Toolbar.Floating.background", JBColor(0xEDEDED, 0x454A4D))
  }
}