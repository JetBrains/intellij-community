// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager

internal class ActionToolbar(actions: List<AnAction>) : JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)) {

  init {
    border = null
    isOpaque = false
    actions.map { createButton(it) }.forEach { add(it)}
  }

  private fun createButton(action: AnAction): JComponent {
    val presentation = action.templatePresentation
    val insets = UIManager.getInsets("MainToolbar.icon.borderInsets") ?: JBUI.insets(10)
    val iconSize = presentation.icon?.let { Dimension(it.iconWidth, it.iconHeight) } ?: Dimension(16, 16)
    val size = Dimension(iconSize.width + insets.left + insets.right,
                         iconSize.height + insets.top + insets.bottom)
    val button = ActionButton(action, presentation, ActionPlaces.MAIN_TOOLBAR, size)
    button.setLook(MainToolbarLook())
    return button
  }
}

private class MainToolbarLook : IdeaActionButtonLook() {

  override fun getStateBackground(component: JComponent, state: Int): Color = when (state) {
    ActionButtonComponent.NORMAL -> component.background
    ActionButtonComponent.PUSHED -> UIManager.getColor("MainToolbar.icon.pressedBackground")
                                    ?: UIManager.getColor("ActionButton.pressedBackground")
    else -> UIManager.getColor("MainToolbar.icon.hoverBackground")
            ?: UIManager.getColor("ActionButton.hoverBackground")
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}
}
