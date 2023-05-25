// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrIcon
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JLabel

@ApiStatus.Internal
internal class LcrIconImpl : LcrIcon, LcrCell {

  override val component = JLabel()

  override fun init(isSelected: Boolean, cellHasFocus: Boolean) {
    icon = null
  }

  override var icon: Icon?
    get() = component.icon
    set(value) {
      component.icon = value
    }
}