// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import java.awt.*
import java.awt.event.*
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.plaf.ComponentUI
import kotlin.math.max
import kotlin.properties.Delegates.observable

class InlineIconButton @JvmOverloads constructor(icon: Icon,
                                                 hoveredIcon: Icon? = null,
                                                 disabledIcon: Icon? = null,
                                                 tooltip: @NlsContexts.Tooltip String? = null,
                                                 var shortcut: ShortcutSet? = null)
  : JComponent() {

  var actionListener: ActionListener? by observable(null) { _, old, new ->
    firePropertyChange(ACTION_LISTENER_PROPERTY, old, new)
  }
  var icon: Icon by observable(icon) { _, old, new ->
    firePropertyChange(ICON_PROPERTY, old, new)
  }
  var hoveredIcon: Icon? by observable(hoveredIcon) { _, old, new ->
    firePropertyChange(HOVERED_ICON_PROPERTY, old, new)
  }
  var disabledIcon: Icon? by observable(disabledIcon) { _, old, new ->
    firePropertyChange(DISABLED_ICON_PROPERTY, old, new)
  }
  var tooltip: @NlsContexts.Tooltip String? by observable(tooltip) { _, old, new ->
    firePropertyChange(TOOL_TIP_TEXT_KEY, old, new)
  }
  var withBackgroundHover: Boolean by observable(false) { _, old, new ->
    firePropertyChange(WITH_BACKGROUND_PROPERTY, old, new)
  }

  init {
    setUI(InlineIconButtonUI())
  }

  private class InlineIconButtonUI : ComponentUI() {

    private var buttonBehavior: BaseButtonBehavior? = null
    private var tooltipConnector: UiNotifyConnector? = null
    private var spaceKeyListener: KeyListener? = null
    private var propertyListener: PropertyChangeListener? = null

    override fun paint(g: Graphics, c: JComponent) {
      val behaviour = buttonBehavior ?: return

      c as InlineIconButton
      val icon = getCurrentIcon(c)

      val g2 = g.create() as Graphics2D
      try {
        val look = if (c.withBackgroundHover) ActionButtonLook.SYSTEM_LOOK else ActionButtonLook.INPLACE_LOOK
        val buttonState = ActionButtonLook.getButtonState(c.isEnabled,
                                                          behaviour.isHovered,
                                                          behaviour.isFocused,
                                                          behaviour.isPressedByMouse,
                                                          behaviour.isPressedByKeyboard)
        if (c.isEnabled || !StartupUiUtil.isUnderDarcula || ExperimentalUI.isNewUI()) {
          look.paintBackground(g2, c, buttonState)
        }
        if (behaviour.isFocused) {
          val rect = Rectangle(c.getSize())
          DarculaUIUtil.paintFocusBorder(g2, rect.width, rect.height, 0f, true)
        }
        else {
          look.paintBorder(g2, c, buttonState)
        }

        val iconPoint = getIconPosition(c, icon)
        icon.paintIcon(c, g2, iconPoint.x, iconPoint.y)
      }
      finally {
        g2.dispose()
      }
    }

    override fun getMinimumSize(c: JComponent): Dimension {
      return getPreferredSize(c)
    }

    override fun getPreferredSize(c: JComponent): Dimension {
      c as InlineIconButton
      val icon = getCurrentIcon(c)
      val size = if (c.withBackgroundHover)
        calculateComponentSizeWithBackground(icon)
      else
        Dimension(icon.iconWidth, icon.iconHeight)

      JBInsets.addTo(size, c.insets)
      return size
    }

    override fun getMaximumSize(c: JComponent): Dimension {
      return getPreferredSize(c)
    }

    private fun getCurrentIcon(c: InlineIconButton): Icon {
      if (!c.isEnabled) return c.disabledIcon ?: IconLoader.getDisabledIcon(c.icon)
      if (buttonBehavior?.isHovered == true || c.hasFocus()) return c.hoveredIcon ?: c.icon
      return c.icon
    }

    override fun installUI(c: JComponent) {
      c as InlineIconButton
      buttonBehavior = object : BaseButtonBehavior(c, null as Void?) {
        override fun execute(e: MouseEvent) {
          if (c.isEnabled) {
            c.actionListener?.actionPerformed(ActionEvent(e.source, ActionEvent.ACTION_PERFORMED, "execute", e.modifiers))
          }
        }
      }.also { it.setupListeners() }
      spaceKeyListener = object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent) {
          if (c.isEnabled && !e.isConsumed && e.modifiers == 0 && e.keyCode == KeyEvent.VK_SPACE) {
            c.actionListener?.actionPerformed(ActionEvent(e.source, ActionEvent.ACTION_PERFORMED, "execute", e.modifiers))
            e.consume()
            return
          }
        }
      }
      c.addKeyListener(spaceKeyListener)

      val tooltipActivatable = object : Activatable {
        override fun showNotify() {
          if (c.tooltip != null) {
            HelpTooltip()
              .setTitle(c.tooltip)
              .setShortcut(c.shortcut?.let { KeymapUtil.getFirstKeyboardShortcutText(it) })
              .installOn(c)
          }
        }

        override fun hideNotify() {
          HelpTooltip.dispose(c)
        }
      }
      tooltipConnector = UiNotifyConnector.installOn(c, tooltipActivatable)

      propertyListener = PropertyChangeListener {
        HelpTooltip.getTooltipFor(c)?.setTitle(c.tooltip)
        c.revalidate()
        c.repaint()
      }
      c.addPropertyChangeListener(propertyListener)

      c.isOpaque = false
      c.isFocusable = true
      c.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun uninstallUI(c: JComponent) {
      tooltipConnector?.let {
        Disposer.dispose(it)
      }
      tooltipConnector = null
      spaceKeyListener?.let {
        c.removeKeyListener(it)
      }
      propertyListener?.let {
        c.removePropertyChangeListener(it)
      }
      propertyListener = null
      spaceKeyListener = null
      buttonBehavior = null
      HelpTooltip.dispose(c)
    }

    private fun getIconPosition(component: JComponent, icon: Icon): Point {
      val rect = Rectangle(component.width, component.height)
      val i = component.insets
      JBInsets.removeFrom(rect, i)

      val x = i.left + (rect.width - icon.iconWidth) / 2
      val y = i.top + (rect.height - icon.iconHeight) / 2

      return Point(x, y)
    }

    private fun calculateComponentSizeWithBackground(icon: Icon): Dimension {
      return if (
        icon.iconWidth < ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width &&
        icon.iconHeight < ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height
      ) {
        ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
      }
      else {
        Dimension(
          max(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.width, icon.iconWidth),
          max(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE.height, icon.iconHeight)
        )
      }
    }
  }

  companion object {
    private const val ACTION_LISTENER_PROPERTY = "action_listener"
    private const val ICON_PROPERTY = "icon"
    private const val HOVERED_ICON_PROPERTY = "hovered_icon"
    private const val DISABLED_ICON_PROPERTY = "disabled_icon"
    private const val WITH_BACKGROUND_PROPERTY = "with_background"
  }
}