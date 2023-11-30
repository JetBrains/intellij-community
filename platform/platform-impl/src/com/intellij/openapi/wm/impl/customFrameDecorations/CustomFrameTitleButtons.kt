// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyle
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyleState
import com.intellij.openapi.wm.impl.customFrameDecorations.style.HOVER_KEY
import com.intellij.openapi.wm.impl.customFrameDecorations.style.StyleManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.JBUI.CurrentTheme
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonUI

internal open class CustomFrameTitleButtons(myCloseAction: Action) {
  companion object {
    fun create(closeAction: Action): CustomFrameTitleButtons {
      val darculaTitleButtons = CustomFrameTitleButtons(closeAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }
  }

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

  private val closeStyleBuilder: ComponentStyle.ComponentStyleBuilder<JButton> = ComponentStyle.ComponentStyleBuilder<JButton> {
    isOpaque = false
    border = Borders.empty()
    icon = AllIcons.Windows.CloseActive
  }.apply {
    style(ComponentStyleState.HOVERED) {
      isOpaque = true
      background = Color(0xe81123)
      icon = AllIcons.Windows.CloseHover
    }
    style(ComponentStyleState.PRESSED) {
      isOpaque = true
      background = Color(0xf1707a)
      icon = AllIcons.Windows.CloseHover
    }
  }
  private val activeCloseStyle = closeStyleBuilder.build()

  private val inactiveCloseStyle = closeStyleBuilder
    .updateDefault {
      icon = AllIcons.Windows.CloseInactive
    }.build()

  private val panel = TitleButtonsPanel()

  val closeButton: JButton = createButton("Close", myCloseAction)

  internal var isCompactMode: Boolean
    set(value) {
      panel.isCompactMode = value
    }
    get() {
      return panel.isCompactMode
    }

  var isSelected: Boolean = false
    set(value) {
      if(field != value) {
        field = value
        updateStyles()
      }
    }

  protected open fun updateStyles() {
    StyleManager.applyStyle(closeButton, if(isSelected) activeCloseStyle else inactiveCloseStyle)
  }

  protected fun createChildren() {
    fillButtonPane()
    addCloseButton()
    updateVisibility()
    updateStyles()
  }

  fun getView(): JComponent = panel

  protected open fun fillButtonPane() {
  }

  open fun updateVisibility() {
  }

  private fun addCloseButton() {
    addComponent(closeButton)
  }

  protected fun addComponent(component: JComponent) {
    panel.addComponent(component)
  }

  protected fun getStyle(icon: Icon, hoverIcon : Icon): ComponentStyle<JComponent> {
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

  protected fun createButton(accessibleName: String, action: Action): JButton {
    val button = object : JButton(){
      init {
        super.setUI(HoveredButtonUI())
      }

      override fun setUI(ui: ButtonUI?) {
      }
    }
    button.action = action
    button.isFocusable = false
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)
    button.text = null
    return button
  }
}
private class HoveredButtonUI : BasicButtonUI() {
  override fun paint(g: Graphics, c: JComponent) {
    getHoverColor(c)?.let {
      g.color = it
      g.fillRect(0, 0, c.width, c.height)
    }
    super.paint(g, c)
  }

  private fun getHoverColor(c: JComponent): Color? = c.getClientProperty(HOVER_KEY) as? Color
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
    if (isCompactMode) {
      size.height = JBUIScale.scale(30)
    }
    preferredSize = Dimension(size.width, size.height)
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