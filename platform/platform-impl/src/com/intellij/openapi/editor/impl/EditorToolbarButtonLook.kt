// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
class EditorToolbarButtonLook(private val editor: Editor) : ActionButtonLook() {
  companion object {
    private val HOVER_BACKGROUND = ColorKey.createColorKey("ActionButton.hoverBackground",
                                                           JBUI.CurrentTheme.ActionButton.hoverBackground())

    private val PRESSED_BACKGROUND = ColorKey.createColorKey("ActionButton.pressedBackground",
                                                             JBUI.CurrentTheme.ActionButton.pressedBackground())
  }

  override fun paintBorder(g: Graphics?, component: JComponent?, state: Int) {}

  override fun paintBorder(g: Graphics?, component: JComponent?, color: Color?) {}

  override fun paintBackground(g: Graphics, component: JComponent, @ActionButtonComponent.ButtonState state: Int) {
    if (state == ActionButtonComponent.NORMAL) {
      return
    }

    val rect = Rectangle(component.size)
    JBInsets.removeFrom(rect, component.getInsets())

    val scheme = editor.getColorsScheme()
    val color = if (state == ActionButtonComponent.PUSHED) scheme.getColor(PRESSED_BACKGROUND) else scheme.getColor(HOVER_BACKGROUND)
    if (color != null) {
      SYSTEM_LOOK.paintLookBackground(g, rect, color)
    }
  }

  override fun paintBackground(g: Graphics?, component: JComponent, color: Color?) {
    SYSTEM_LOOK.paintBackground(g, component, color)
  }

  override fun paintIcon(g: Graphics?, actionButton: ActionButtonComponent?, icon: Icon?, x: Int, y: Int) {
    if (icon != null) {
      val isDark = ColorUtil.isDark(editor.getColorsScheme().getDefaultBackground())
      super.paintIcon(g, actionButton, IconLoader.getDarkIcon(icon, isDark), x, y)
    }
  }
}