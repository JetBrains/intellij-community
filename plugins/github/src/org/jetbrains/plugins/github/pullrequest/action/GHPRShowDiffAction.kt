// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.diff.DiffDialogHints
import com.intellij.diff.DiffManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelper

class GHPRShowDiffAction : DumbAwareAction() {

  override fun update(e: AnActionEvent) {
    val project = e.project
    val selection = e.getData(VcsDataKeys.CHANGES)
    e.presentation.isEnabled = project != null && selection?.isNotEmpty() == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val selection = e.getRequiredData(VcsDataKeys.CHANGES)
    if (selection.isEmpty()) return

    val diffHelper = e.getData(GHPRChangesDiffHelper.DATA_KEY)
    val actionDataContext = e.getData(GHPRActionKeys.ACTION_DATA_CONTEXT)
    val producers = selection.mapNotNull {
      GHPRChangesDiffHelper.getDiffRequestProducer(project, it, diffHelper, actionDataContext)
    }
    if (producers.isEmpty()) return

    DiffManager.getInstance().showDiff(project, ChangeDiffRequestChain(producers, 0), DiffDialogHints.DEFAULT)
  }
}