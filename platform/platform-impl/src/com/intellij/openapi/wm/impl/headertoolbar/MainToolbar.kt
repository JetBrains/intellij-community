// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbarWidgetFactory.Position
import com.intellij.ui.components.panels.HorizontalLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager

class MainToolbar: JPanel(HorizontalLayout(10)) {

  val layoutMap = mapOf(
    Position.Left to HorizontalLayout.LEFT,
    Position.Right to HorizontalLayout.RIGHT,
    Position.Center to HorizontalLayout.CENTER
  )

  init {
    background = UIManager.getColor("MainToolbar.background")
    isOpaque = true
    for (factory in MainToolbarWidgetFactory.EP_NAME.extensionList) {
      val widget = factory.createWidget()
      add(layoutMap[factory.getPosition()], widget)
    }

    createActionsBar()?.let { add(HorizontalLayout.RIGHT, it) }
  }

  private fun createActionsBar(): JComponent? {
    val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR_ACTIONS) as ActionGroup?
    return group?.let { ActionToolbar(it.getChildren(null).asList()) }
  }
}