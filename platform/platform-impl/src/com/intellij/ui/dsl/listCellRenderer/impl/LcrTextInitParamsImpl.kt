// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Font
import javax.swing.UIManager

@ApiStatus.Internal
internal class LcrTextInitParamsImpl(accessibleName: @Nls String?, override var foreground: Color) :
  LcrInitParamsImpl(accessibleName), LcrTextInitParams {

  override var attributes: SimpleTextAttributes? = null

  override var font: Font? = UIManager.getFont("Label.font")

  fun isSimpleText(): Boolean {
    return attributes == null
  }
}
