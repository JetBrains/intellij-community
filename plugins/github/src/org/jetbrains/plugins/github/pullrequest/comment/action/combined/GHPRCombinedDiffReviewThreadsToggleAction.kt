// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.action.combined

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.toActionName
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport

internal class GHPRCombinedDiffReviewThreadsToggleAction : ActionGroup() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val model: CombinedDiffPreviewModel? = e.getData(GHPRActionKeys.COMBINED_DIFF_PREVIEW_MODEL)
    val reviewSupports = model?.getLoadedRequests()?.mapNotNull { it.getUserData(GHPRDiffReviewSupport.KEY) }
    e.presentation.isEnabledAndVisible = !reviewSupports.isNullOrEmpty()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val model: CombinedDiffPreviewModel = e?.getRequiredData(GHPRActionKeys.COMBINED_DIFF_PREVIEW_MODEL) ?: return emptyArray<AnAction>()
    return DiscussionsViewOption.values().map { viewOption -> ToggleOptionAction(model, viewOption) }.toTypedArray()
  }

  private class ToggleOptionAction(
    private val model: CombinedDiffPreviewModel,
    private val viewOption: DiscussionsViewOption
  ) : ToggleAction(viewOption.toActionName()) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean {
      val diffReviewSupport: GHPRDiffReviewSupport = e.getRequiredData(GHPRDiffReviewSupport.DATA_KEY)
      return diffReviewSupport.discussionsViewOption == viewOption
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      model.getLoadedRequests().forEach { request ->
        request.getUserData(GHPRDiffReviewSupport.KEY)?.discussionsViewOption = viewOption
      }
    }
  }
}
