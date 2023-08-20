// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons.FrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons.LinuxFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.frameTitleButtons.WindowsFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyle
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyleState
import com.intellij.openapi.wm.impl.customFrameDecorations.style.StyleManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.JBUI.CurrentTheme
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*


internal open class CustomFrameTitleButtons(private val myCloseAction: Action,
                                            private val myRestoreAction: Action? = null,
                                            private val myIconifyAction: Action? = null,
                                            private val myMaximizeAction: Action? = null
) {
  companion object {
    fun create(myCloseAction: Action,
               myRestoreAction: Action? = null,
               myIconifyAction: Action? = null,
               myMaximizeAction: Action? = null): CustomFrameTitleButtons {
      val darculaTitleButtons = CustomFrameTitleButtons(myCloseAction, myRestoreAction, myIconifyAction, myMaximizeAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

  private val buttons: FrameTitleButtons = if (SystemInfo.isWindows)
    WindowsFrameTitleButtons(myCloseAction, myRestoreAction, myIconifyAction, myMaximizeAction)
  else
    LinuxFrameTitleButtons(myCloseAction, myRestoreAction, myIconifyAction, myMaximizeAction)

  private val baseStyle = ComponentStyle.ComponentStyleBuilder<JComponent> {
    isOpaque = false
    border = Borders.empty()
    hover = null
  }.apply {
    style(ComponentStyleState.HOVERED) {
      hover = CurrentTheme.CustomFrameDecorations.titlePaneButtonHoverBackground()
    }
    style(ComponentStyleState.PRESSED) {
      hover = CurrentTheme.CustomFrameDecorations.titlePaneButtonPressBackground()
    }
  }


  private val panel = TitleButtonsPanel()


  internal var isCompactMode: Boolean
    set(value) {
      panel.isCompactMode = value
    }
    get() {
      return panel.isCompactMode
    }

  var isSelected: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        updateStyles()
      }
    }

  protected open fun updateStyles() {
    StyleManager.applyStyle(
      buttons.closeButton,
      getStyle(
        if (isSelected) buttons.closeIcon else buttons.closeInactiveIcon,
        buttons.closeHoverIcon
      )
    )
    buttons.restoreButton?.let {
      StyleManager.applyStyle(
        it,
        getStyle(
          if (isSelected) buttons.restoreIcon else buttons.restoreInactiveIcon,
          buttons.restoreIcon
        )
      )
    }
    buttons.maximizeButton?.let {
      StyleManager.applyStyle(
        it,
        getStyle(
          if (isSelected) buttons.maximizeIcon else buttons.maximizeInactiveIcon,
          buttons.maximizeIcon
        )
      )
    }
    buttons.minimizeButton?.let {
      StyleManager.applyStyle(
        it,
        getStyle(
          if (isSelected) buttons.minimizeIcon else buttons.minimizeInactiveIcon,
          buttons.minimizeIcon
        )
      )
    }
  }

  protected fun createChildren() {
    fillButtonPane()
    updateVisibility()
    updateStyles()
  }

  fun getView(): JComponent = panel

  protected open fun fillButtonPane() {
    if (SystemInfo.isLinux) {
      var linuxButtonsLayout = LinuxLookAndFeel.getHeaderLayout()
      if (!linuxButtonsLayout.contains("close")) {
        linuxButtonsLayout = linuxButtonsLayout.plus("close")
      }
      for (item in linuxButtonsLayout) {
        when (item) {
          "minimize" -> buttons.minimizeButton?.let { panel.addComponent(it) }
          "maximize" -> {
            buttons.maximizeButton?.let { panel.addComponent(it) }
            buttons.restoreButton?.let { panel.addComponent(it) }
          }
          "close" -> panel.addComponent(buttons.closeButton)
        }
      }
      val emptyComponent: Box = Box.createHorizontalBox() // Margin right
      panel.addComponent(emptyComponent)
    } else {
      buttons.minimizeButton?.let { panel.addComponent(it) }
      buttons.maximizeButton?.let { panel.addComponent(it) }
      buttons.restoreButton?.let { panel.addComponent(it) }
      panel.addComponent(buttons.closeButton)
    }
  }

  open fun updateVisibility() {
    buttons.minimizeButton?.isVisible = myIconifyAction?.isEnabled ?: false
    buttons.restoreButton?.isVisible = myRestoreAction?.isEnabled ?: false
    buttons.maximizeButton?.isVisible = myMaximizeAction?.isEnabled ?: false
  }

  protected fun getStyle(icon: Icon, hoverIcon: Icon): ComponentStyle<JComponent> {
    val clone = baseStyle.clone()
    clone.updateDefault {
      this.icon = icon
    }

    clone.updateState(ComponentStyleState.HOVERED) {
      this.icon = hoverIcon
    }

    clone.updateState(ComponentStyleState.PRESSED) {
      this.icon = hoverIcon
    }
    return clone.build()
  }

}

private class TitleButtonsPanel : JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) {
  var isCompactMode = false
    set(value) {
      field = value
      updateScaledPreferredSize()
    }

  init {
    isOpaque = false
  }

  fun addComponent(component: JComponent) {
    component.setScaledPreferredSize()
    add(component, "top")
  }

  private fun updateScaledPreferredSize() {
    components.forEach { (it as? JComponent)?.setScaledPreferredSize() }
  }

  private fun JComponent.setScaledPreferredSize() {
    val size = CurrentTheme.TitlePane.buttonPreferredSize(UISettings.defFontScale).clone() as Dimension
    // TODO isCompactMode siempre es false, parece un bug
    if (isCompactMode) {
      size.height = JBUIScale.scale(30)
    }
    if (SystemInfo.isLinux) {
      if (this !is Box) {
        preferredSize = Dimension(38, size.height)
      } else {
        preferredSize = Dimension(1, size.height) // Margin right
      }
    } else {
      preferredSize = Dimension(size.width, size.height)
    }
  }

  override fun updateUI() {
    super.updateUI()
    components?.forEach { component ->
      if (component is JComponent) {
        component.setScaledPreferredSize()
      }
    }
  }
}