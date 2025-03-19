// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle.messagePointer
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory.SELECTED_CHANGES
import com.intellij.collaboration.ui.codereview.details.model.isViewedStateForAllChanges
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions.ActionText
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
    val changes = e.getData(SELECTED_CHANGES) ?: return
    val vm = e.getData(GHPRChangeListViewModel.DATA_KEY) ?: return

    if (!vm.isOnLatest) return

    e.presentation.isEnabledAndVisible = vm.isViewedStateForAllChanges(changes, !isViewed)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val changes = e.getData(SELECTED_CHANGES) ?: return
    val vm = e.getData(GHPRChangeListViewModel.DATA_KEY) ?: return
    vm.setViewedState(changes, isViewed)
  }
}

internal class GHPRMarkFilesViewedAction :
  GHPRViewedStateAction(messagePointer("action.CodeReview.MarkChangesViewed.text"), true)

internal class GHPRMarkFilesNotViewedAction :
  GHPRViewedStateAction(messagePointer("action.CodeReview.MarkChangesNotViewed.text"), false)