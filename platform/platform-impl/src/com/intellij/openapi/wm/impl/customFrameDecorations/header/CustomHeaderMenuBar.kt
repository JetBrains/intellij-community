// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import javax.swing.border.Border

class CustomHeaderMenuBar : IdeMenuBar() {
  init {
    isOpaque = false
  }

  override fun getBorder(): Border? {
    return JBUI.Borders.empty()
  }

  override fun paintBackground(g: Graphics?) {
  }

  override fun createActionMenu(enableMnemonics: Boolean, isDarkMenu: Boolean, action: ActionGroup?): ActionMenu {
    val actionMenu = super.createActionMenu(enableMnemonics, isDarkMenu, action)
    actionMenu.isOpaque = false

    return actionMenu
  }
 }