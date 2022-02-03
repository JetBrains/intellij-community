// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import java.awt.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager

private const val iconSize = 20

internal class ActionToolbar(actions: List<AnAction>) : JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)) {

  init {
    border = null
    isOpaque = false
    actions.map { createButton(it) }.forEach { add(it)}
  }

  private fun createButton(action: AnAction): JComponent {
    val insets = UIManager.getInsets("MainToolbar.Icon.borderInsets") ?: JBUI.insets(10)

    val presentation = action.templatePresentation.clone()
    presentation.scaleIcon(iconSize)
    presentation.addPropertyChangeListener { evt -> if (evt.propertyName == Presentation.PROP_ICON) presentation.scaleIcon(iconSize) }
    if (presentation.icon == null) presentation.icon = AllIcons.Toolbar.Unknown

    val size = Dimension(iconSize + insets.left + insets.right,
                         iconSize + insets.top + insets.bottom)
    val button = ActionButton(action, presentation, ActionPlaces.MAIN_TOOLBAR, size)
    button.setLook(MainToolbarLook())
    return button
  }

  private fun Presentation.scaleIcon(size: Int) {
    if (icon == null) return
    if (icon is ScalableIcon && icon.iconWidth != size) {
      icon = IconLoader.loadCustomVersionOrScale(icon as ScalableIcon, size.toFloat())
    }
  }
}

private class MainToolbarLook : IdeaActionButtonLook() {

  override fun getStateBackground(component: JComponent, state: Int): Color = when (state) {
    ActionButtonComponent.NORMAL -> component.background
    ActionButtonComponent.PUSHED -> UIManager.getColor("MainToolbar.Icon.pressedBackground")
                                    ?: UIManager.getColor("ActionButton.pressedBackground")
    else -> UIManager.getColor("MainToolbar.Icon.hoverBackground")
            ?: UIManager.getColor("ActionButton.hoverBackground")
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}

  override fun getButtonArc() = JBValue.Float(0f)
}
