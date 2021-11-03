// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import com.intellij.ui.components.panels.HorizontalLayout
import javax.swing.JComponent
import javax.swing.JPanel

class MainToolbar: JPanel(HorizontalLayout(10)) {

  val layoutMap = mapOf(
    Position.Left to HorizontalLayout.LEFT,
    Position.Right to HorizontalLayout.RIGHT,
    Position.Center to HorizontalLayout.CENTER
  )

  init {
    for (factory in MainToolbarWidgetFactory.EP_NAME.extensionList) {
      val widget = factory.createWidget()
      add(layoutMap[factory.getPosition()], widget)
    }

    add(HorizontalLayout.RIGHT, createActionsBar())
  }

  private fun createActionsBar(): JComponent {
    val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR_ACTIONS) as ActionGroup?
    val toolBar = ActionManagerEx.getInstanceEx()
      .createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group!!, true)

    return toolBar.component
  }
}