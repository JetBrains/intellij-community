// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTextArea
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import javax.swing.ScrollPaneConstants

class VcsLogShowBigRepositoriesList: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val repositories = VcsLogBigRepositoriesList.getInstance().repositoryPaths
    val textArea = JBTextArea(repositories.joinToString("\n"), 15, 80).also {
      it.lineWrap = true
      it.isEditable = false
    }
    DialogBuilder().apply {
      setCenterPanel(ScrollPaneFactory.createScrollPane(textArea,
                                                        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED))
      setTitle(VcsLogBundle.message("vcs.log.big.repositories.dialog.title"))
    }.show()
  }
}