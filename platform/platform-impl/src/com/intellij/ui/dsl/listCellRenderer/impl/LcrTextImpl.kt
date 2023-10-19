// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.JLabel

@ApiStatus.Internal
internal class LcrTextImpl : LcrCellBaseImpl() {

  override val component: JLabel = JLabel()

  fun init(text: @Nls String, initParams: LcrTextInitParams, selected: Boolean, rowForeground: Color) {
    component.text = text
    component.font = initParams.font
    component.foreground = if (selected) rowForeground else initParams.foreground
    component.accessibleContext.accessibleName = initParams.accessibleName
  }
}
