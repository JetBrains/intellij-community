// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrCellBase
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JList

@ApiStatus.Internal
internal sealed class LcrCellBaseImpl : LcrCellBase {

  override var visible: Boolean
    get() = component.isVisible
    set(value) {
      component.isVisible = value
    }

  abstract val component: JComponent

  open fun init(list: JList<*>, isSelected: Boolean, cellHasFocus: Boolean) {
  }
}