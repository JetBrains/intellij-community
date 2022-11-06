// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.ui.SearchTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.UIManager
import javax.swing.plaf.PanelUI

/**
 * Component is not finished yet
 */
@ApiStatus.Experimental
class SearchFieldWithExtension(
  extensionComponent: JComponent,
  private val searchTextField: SearchTextField
) : JPanel(BorderLayout()) {
  val textField: JTextField
    get() = searchTextField.textEditor

  init {
    extensionComponent.apply {
      border = JBUI.Borders.empty()
      isOpaque = false
    }

    searchTextField.isOpaque = false

    textField.apply {
      border = JBUI.Borders.empty()
      isOpaque = false

      addFocusListener(object : FocusListener {
        override fun focusLost(e: FocusEvent?) = repaint()
        override fun focusGained(e: FocusEvent?) = repaint()
      })
    }

    val contentPanel = BorderLayoutPanel()
      .addToCenter(searchTextField)
      .addToRight(extensionComponent)
      .andTransparent().apply {
        border = JBUI.Borders.empty(1)
      }
    add(contentPanel, BorderLayout.CENTER)
  }

  override fun getUIClassID(): String = UI_CLASS_ID
  override fun getUI(): DarculaSearchFieldWithExtensionUI = super.getUI() as DarculaSearchFieldWithExtensionUI

  override fun requestFocus(): Unit = textField.requestFocus()

  override fun setBackground(bg: Color?) = super.setBackground(UIUtil.getTextFieldBackground())

  override fun hasFocus(): Boolean = textField.hasFocus()

  companion object {
    private const val UI_CLASS_ID = "SearchFieldWithExtensionUI"
  }
}

@Suppress("unused")
@ApiStatus.Experimental
class DarculaSearchBorder : DarculaTextBorder() {
  override fun paintBorder(c: Component?, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (c !is SearchFieldWithExtension) return
    val enabled = c.textField.isEnabled && c.textField.isEditable
    val r = Rectangle(c.size)
    paintDarculaSearchArea(g as Graphics2D?, r, c as JComponent?, true, enabled)
  }
}

@Suppress("unused")
@ApiStatus.Experimental
class DarculaSearchFieldWithExtensionUI : PanelUI() {
  companion object {
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun createUI(c: JComponent?) = DarculaSearchFieldWithExtensionUI()
  }

  override fun installUI(c: JComponent?) {
    super.installUI(c)
    c ?: return
    c.border = UIManager.getBorder("SearchFieldWithExtension.border")
    c.background = UIManager.getColor("SearchFieldWithExtension.background")
  }

  override fun update(g: Graphics?, c: JComponent?) {
    // ComponentUI.update draws full-size rect in case of component isOpaque == true
    // Current inheritance avoids that
    paint(g, c)
  }

  override fun paint(g: Graphics?, c: JComponent?) {
    // background painted by DarculaTextBorder.paintDarculaSearchArea
  }
}