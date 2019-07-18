// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyle
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyleState
import com.intellij.openapi.wm.impl.customFrameDecorations.style.StyleManager
import com.intellij.ui.JBColor
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Color
import java.awt.Dimension
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonUI

open class CustomFrameTitleButtons constructor(myCloseAction: Action) {
  companion object {
    private val closeIcon = freezeIconUserSize(AllIcons.Windows.CloseActive)
    private val closeHoverIcon = freezeIconUserSize(AllIcons.Windows.CloseHover)
    private val closeInactive = freezeIconUserSize(AllIcons.Windows.CloseInactive)

    fun create(myCloseAction: Action): CustomFrameTitleButtons {
      val darculaTitleButtons = CustomFrameTitleButtons(myCloseAction)
      darculaTitleButtons.createChildren()
      return darculaTitleButtons
    }

    fun freezeIconUserSize(icon: Icon): Icon {
      return IconUtil.overrideScale(IconUtil.deepCopy(icon, null), ScaleType.USR_SCALE.of(1.0))
    }
  }

  private val baseStyle = ComponentStyle.ComponentStyleBuilder<JComponent> {
    isOpaque = false
    border = JBUI.Borders.empty()
  }.apply {
    style(ComponentStyleState.HOVERED) {
      isOpaque = true
      background = JBColor(0xd1d1d1, 0x54585a)
    }
    style(ComponentStyleState.PRESSED) {
      isOpaque = true
      background = JBColor(0xb5b5b5, 0x686e70)
    }
  }

  val closeStyleBuilder = ComponentStyle.ComponentStyleBuilder<JButton> {
    isOpaque = false
    border = JBUI.Borders.empty()
    icon = closeIcon
  }.apply {
    style(ComponentStyleState.HOVERED) {
      isOpaque = true
      background = Color(0xe81123)
      icon = closeHoverIcon
    }
    style(ComponentStyleState.PRESSED) {
      isOpaque = true
      background = Color(0xf1707a)
      icon = closeHoverIcon
    }
  }
  private val activeCloseStyle = closeStyleBuilder.build()

  private val inactiveCloseStyle = closeStyleBuilder
    .updateDefault() {
      icon = closeInactive
    }.build()

  protected val panel = JPanel(MigLayout("top, ins 0 2 0 0, gap 0, hidemode 3, novisualpadding")).apply {
    isOpaque = false
  }

  private val myCloseButton: JButton = createButton("Close", myCloseAction)

  var isSelected = false
    set(value) {
      if(field != value) {
        field = value
        updateStyles()
      }
    }

  protected open fun updateStyles() {
    StyleManager.applyStyle(myCloseButton, if(isSelected) activeCloseStyle else inactiveCloseStyle)
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
    addComponent(myCloseButton)
  }

  protected fun addComponent(component: JComponent) {
    component.preferredSize = Dimension(47, 28)
    panel.add(component, "top")
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
        super.setUI(BasicButtonUI())
      }

      override fun setUI(ui: ButtonUI?) {
      }
    }
    button.action = action
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)
    button.text = null
    return button
  }
}