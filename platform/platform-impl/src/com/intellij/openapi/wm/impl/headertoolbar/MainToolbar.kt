// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.GridBag
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

class MainToolbar(project: Project?): JPanel(GridBagLayout()), Disposable {

  init {
    val projectWidget = ProjectWidget(project)
    Disposer.register(this, projectWidget)

    val gb = GridBag().nextLine()
    add(projectWidget, gb.next().fillCellNone().weightx(1.0).anchor(GridBagConstraints.CENTER))
    add(createActionsBar(), gb.next().fillCell().anchor(GridBagConstraints.EAST))
  }

  private fun createActionsBar(): JComponent {
    val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EXPERIMENTAL_TOOLBAR_ACTIONS) as ActionGroup?
    val toolBar = ActionManagerEx.getInstanceEx()
      .createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group!!, true)

    return toolBar.component
  }

  override fun dispose() {}
}