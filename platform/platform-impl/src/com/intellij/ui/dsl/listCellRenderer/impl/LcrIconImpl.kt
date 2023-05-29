// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList

@ApiStatus.Internal
internal class LcrIconImpl : LcrCellBaseImpl(), LcrIcon {

  override val component = JLabel()

  override fun init(list: JList<*>, isSelected: Boolean, cellHasFocus: Boolean) {
    super.init(list, isSelected, cellHasFocus)

    icon = null
  }

  override var icon: Icon?
    get() = component.icon
    set(value) {
      component.icon = value
      visible = value != null
    }
}