// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelper

class GHPRShowDiffAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val selection = e.getData(VcsDataKeys.CHANGES_SELECTION)
    val diffHelper = e.getData(GHPRChangesDiffHelper.DATA_KEY)
    e.presentation.isEnabled = project != null && selection?.isEmpty == false && diffHelper != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val selection = e.getRequiredData(VcsDataKeys.CHANGES_SELECTION)
    if (selection.isEmpty) return

    val diffHelper = e.getRequiredData(GHPRChangesDiffHelper.DATA_KEY)
    val requestChain = diffHelper.getRequestChain(selection)
    DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.DEFAULT)
  }
}