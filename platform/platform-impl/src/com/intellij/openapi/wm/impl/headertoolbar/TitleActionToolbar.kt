// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

private const val iconSize = 20

internal class TitleActionToolbar(place: String, actionGroup: ActionGroup, horizontal: Boolean)
  : ActionToolbarImpl(place, actionGroup, horizontal) {

  override fun createToolbarButton(action: AnAction,
                                   look: ActionButtonLook?,
                                   place: String,
                                   presentation: Presentation,
                                   minimumSize: Dimension): ActionButton {
    scalePresentationIcons(presentation)
    if (presentation.icon == null) presentation.icon = AllIcons.Toolbar.Unknown

    val insets = UIManager.getInsets("MainToolbar.Icon.borderInsets") ?: JBUI.insets(10)
    val size = Dimension(iconSize + insets.left + insets.right,
                         iconSize + insets.top + insets.bottom)
    val button = ActionButton(action, presentation, ActionPlaces.MAIN_TOOLBAR, size)
    button.setLook(expToolbarButtonLook)
    return button
  }

  override fun isAlignmentEnabled(): Boolean = false
}

val expToolbarButtonLook: ActionButtonLook = object : IdeaActionButtonLook() {
  override fun getStateBackground(component: JComponent, state: Int): Color = when (state) {
    ActionButtonComponent.NORMAL -> component.background
    ActionButtonComponent.PUSHED -> UIManager.getColor("MainToolbar.Icon.pressedBackground")
                                    ?: UIManager.getColor("ActionButton.pressedBackground")
    else -> UIManager.getColor("MainToolbar.Icon.hoverBackground")
            ?: UIManager.getColor("ActionButton.hoverBackground")
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {}
  override fun getButtonArc(): JBValue = JBValue.Float(0f)
}

fun scalePresentationIcons(presentation: Presentation) {
  presentation.scaleIcon({ icon }, { icon = it }, iconSize)
  presentation.scaleIcon({ hoveredIcon }, { hoveredIcon = it }, iconSize)
  presentation.addPropertyChangeListener { evt ->
    if (evt.propertyName == Presentation.PROP_ICON) presentation.scaleIcon({ icon }, { icon = it }, iconSize)
    if (evt.propertyName == Presentation.PROP_HOVERED_ICON) presentation.scaleIcon({ hoveredIcon }, { hoveredIcon = it }, iconSize)
  }
}

private fun Presentation.scaleIcon(getter: Presentation.() -> Icon?, setter: Presentation.(Icon) -> Unit, size: Int) {
  val currentIcon = this.getter()
  if (currentIcon == null) return

  if (currentIcon is ScalableIcon && currentIcon.iconWidth != size) {
    this.setter(IconLoader.loadCustomVersionOrScale(currentIcon, size.toFloat()))
  }
}