// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.UIManager

@ApiStatus.Internal
internal class LcrTextImpl : LcrCellBaseImpl() {

  override val component: JLabel = JLabel()

  fun <T> init(text: @Nls String, initParams: LcrTextInitParamsImpl, list: JList<out T>, selected: Boolean, hasFocus: Boolean) {
    component.text = text
    // Restore default font, so IDE scaling is applied as well
    component.font = UIManager.getFont("Label.font")

    val defaultColor = if (selected) JBUI.CurrentTheme.List.Selection.foreground(hasFocus) else list.foreground
    component.foreground = when (initParams.style) {
      LcrTextInitParams.Style.NORMAL -> defaultColor
      LcrTextInitParams.Style.GRAYED -> if (selected) defaultColor else NamedColorUtil.getInactiveTextColor()
    }
  }
}
