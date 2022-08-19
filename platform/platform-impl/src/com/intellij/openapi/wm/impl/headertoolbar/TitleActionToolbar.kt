// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.UIManager

private val iconSize: Int
  get() = JBUI.scale(20)

internal class TitleActionToolbar(place: String, actionGroup: ActionGroup, horizontal: Boolean)
  : ActionToolbarImpl(place, actionGroup, horizontal) {

  init {
    setCustomButtonLook(HeaderToolbarButtonLook())
    val insets = UIManager.getInsets("MainToolbar.Icon.borderInsets") ?: JBUI.insets(10)
    val size = Dimension(iconSize + insets.left + insets.right,
                         iconSize + insets.top + insets.bottom)
    setMinimumButtonSize(size)
    setActionButtonBorder(JBUI.Borders.empty())
  }

  override fun applyToolbarLook(look: ActionButtonLook?, presentation: Presentation, component: JComponent) {
    super.applyToolbarLook(look, presentation, component)
    scalePresentationIcons(presentation)
  }

  override fun isAlignmentEnabled(): Boolean = false
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