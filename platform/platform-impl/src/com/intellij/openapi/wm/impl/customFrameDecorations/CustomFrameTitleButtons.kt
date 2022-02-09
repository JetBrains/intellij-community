// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyle
import com.intellij.openapi.wm.impl.customFrameDecorations.style.ComponentStyleState
import com.intellij.openapi.wm.impl.customFrameDecorations.style.StyleManager
import com.intellij.ui.scale.ScaleType
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.JBUI.CurrentTheme
import net.miginfocom.swing.MigLayout
import java.awt.*
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.ButtonUI
import javax.swing.plaf.basic.BasicButtonUI

internal open class CustomFrameTitleButtons constructor(myCloseAction: Action) {
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
      return IconUtil.overrideScale(IconUtil.deepCopy(icon, null), ScaleType.USR_SCALE.of(UISettings.defFontScale.toDouble()))
    }
  }

  private val baseStyle = ComponentStyle.ComponentStyleBuilder<JComponent> {
    isOpaque = false
    border = Borders.empty()
  }.apply {
    fun paintHover(g: Graphics, width: Int, height: Int, color: Color) {
      g.color = color
      g.fillRect(0, 0, width, height)
    }

    class MyBorder(val color: ()-> Color) : Border {
      override fun getBorderInsets(c: Component?): Insets = JBInsets.emptyInsets()

      override fun isBorderOpaque(): Boolean = false

      override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        paintHover(g, width, height, color())
      }
    }

    val hoverBorder = MyBorder {CurrentTheme.CustomFrameDecorations.titlePaneButtonHoverBackground()}
    val pressBorder = MyBorder {CurrentTheme.CustomFrameDecorations.titlePaneButtonPressBackground()}

    style(ComponentStyleState.HOVERED) {
      this.border = hoverBorder

    }
    style(ComponentStyleState.PRESSED) {
      this.border = pressBorder
    }
  }

  val closeStyleBuilder = ComponentStyle.ComponentStyleBuilder<JButton> {
    isOpaque = false
    border = Borders.empty()
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
    component.preferredSize = UIManager.getDimension("TitlePane.Button.preferredSize")
                              ?: Dimension((47 * UISettings.defFontScale).toInt(), (28 * UISettings.defFontScale).toInt())
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
    button.isFocusable = false
    button.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, accessibleName)
    button.text = null
    return button
  }
}