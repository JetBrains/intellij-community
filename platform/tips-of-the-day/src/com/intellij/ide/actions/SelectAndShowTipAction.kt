// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.PopupChooserBuilder
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList

class SelectAndShowTipAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val tips = TipAndTrickBean.EP_NAME.extensionList.sortedBy { tip -> tip.id.lowercase() }
    @Suppress("HardCodedStringLiteral", "DialogTitleCapitalization") // it is internal action
    PopupChooserBuilder(JBList(tips))
      .setItemChosenCallback { tip -> TipAndTrickManager.getInstance().showTipDialog(project, tip) }
      .setNamerForFiltering { tip -> tip.id }
      .setRenderer(SimpleListCellRenderer.create("") { tip -> tip.id })
      .setCloseOnEnter(true)
      .setResizable(true)
      .setMovable(true)
      .setTitle("Select the tip to show")
      .createPopup()
      .showInFocusCenter()
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}