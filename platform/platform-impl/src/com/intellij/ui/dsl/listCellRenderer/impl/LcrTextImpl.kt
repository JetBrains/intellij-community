// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JLabel
import javax.swing.UIManager

@ApiStatus.Internal
internal class LcrTextImpl : LcrCellBaseImpl() {

  override val component: JLabel = JLabel()

  fun init(text: @Nls String, initParams: LcrTextInitParamsImpl) {
    component.text = text
    // Restore default font, so IDE scaling is applied as well
    component.font = UIManager.getFont("Label.font")
    component.foreground = initParams.foreground
  }
}
