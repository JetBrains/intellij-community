// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle.messagePointer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions.ActionText
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys.PULL_REQUEST_FILES
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import java.util.function.Supplier

internal abstract class GHPRViewedStateAction(
  dynamicText: Supplier<@ActionText String>,
  private val isViewed: Boolean
) : DumbAwareAction(dynamicText) {
  // viewed state requires EDT for now
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = false
    val files = e.getData(PULL_REQUEST_FILES) ?: return
    val vm = e.getData(GHPRChangeListViewModel.DATA_KEY) ?: return

    e.presentation.isEnabledAndVisible = vm.isViewedStateForAllFiles(files, isViewed)?.not() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val files = e.getRequiredData(PULL_REQUEST_FILES)
    val vm = e.getRequiredData(GHPRChangeListViewModel.DATA_KEY)
    vm.setViewedState(files, isViewed)
  }
}

internal class GHPRMarkFilesViewedAction :
  GHPRViewedStateAction(messagePointer("action.CodeReview.MarkChangesViewed.text"), true)

internal class GHPRMarkFilesNotViewedAction :
  GHPRViewedStateAction(messagePointer("action.CodeReview.MarkChangesNotViewed.text"), false)