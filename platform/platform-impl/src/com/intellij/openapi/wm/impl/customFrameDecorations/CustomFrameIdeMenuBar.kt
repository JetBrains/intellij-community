// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.util.ui.JBUI
import javax.swing.border.Border


class CustomFrameIdeMenuBar(actionManager: ActionManagerEx, dataManager: DataManager, disposable: Disposable) : IdeMenuBar(actionManager,
                                                                                                                           dataManager) {
  init {
  }

/*
  override fun setFont(font: Font) {
    val min = Math.min(font.size, 12)
    val scaledSize = Math.round(min / UISettings.defFontScale)
    super.setFont(Font(font.name, font.style, scaledSize))
  }*/

  override fun getBorder(): Border {
    return JBUI.Borders.empty()
  }
}