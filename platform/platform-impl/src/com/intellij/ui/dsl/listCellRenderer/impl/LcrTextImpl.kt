// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrText
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JList

@ApiStatus.Internal
internal class LcrTextImpl(private val initParams: LcrTextInitParamsImpl) : LcrCellBaseImpl(), LcrText {

  override val component = JLabel()

  override fun init(list: JList<*>, isSelected: Boolean, cellHasFocus: Boolean) {
    super.init(list, isSelected, cellHasFocus)

    text = null
    val defaultColor = if (isSelected) JBUI.CurrentTheme.List.Selection.foreground(cellHasFocus) else list.foreground
    color = when (initParams.style) {
      LcrTextInitParams.Style.NORMAL -> defaultColor
      LcrTextInitParams.Style.GRAYED -> if (isSelected) defaultColor else NamedColorUtil.getInactiveTextColor()
    }
  }

  override var text: @Nls String?
    get() = component.text
    set(value) {
      component.text = value
      visible = value != null
    }

  override var color: Color?
    get() = component.foreground
    set(value) {
      component.foreground = value
    }
}
