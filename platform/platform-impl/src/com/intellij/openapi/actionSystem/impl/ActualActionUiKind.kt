// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUiKind
import javax.swing.JComponent
import javax.swing.MenuElement
import javax.swing.SwingConstants

internal interface ActualActionUiKind : ActionUiKind {
  val component: JComponent

  class Toolbar(val toolbar: ActionToolbar) : ActualActionUiKind, ActionUiKind.Toolbar {
    override val component: JComponent
      get() = toolbar.component

    override fun isHorizontal(): Boolean = toolbar.getOrientation() == SwingConstants.HORIZONTAL
  }

  class Menu(val menu: MenuElement, val mainMenu: Boolean) : ActualActionUiKind, ActionUiKind.Popup {
    override val component: JComponent
      get() = menu.component as JComponent

    override fun isMainMenu(): Boolean = mainMenu
  }
}