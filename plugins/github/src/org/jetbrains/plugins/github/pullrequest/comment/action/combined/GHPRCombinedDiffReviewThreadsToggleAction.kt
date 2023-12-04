// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.action.combined

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.toActionName
import com.intellij.diff.tools.combined.CombinedDiffModel
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport

internal class GHPRCombinedDiffReviewThreadsToggleAction : ActionGroup(), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val model: CombinedDiffModel? = e.getData(GHPRActionKeys.COMBINED_DIFF_PREVIEW_MODEL)
    val reviewSupports = model?.getLoadedRequests()?.mapNotNull { it.getUserData(GHPRDiffReviewSupport.KEY) }
    e.presentation.isEnabledAndVisible = !reviewSupports.isNullOrEmpty()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val model: CombinedDiffModel = e?.getData(GHPRActionKeys.COMBINED_DIFF_PREVIEW_MODEL) ?: return emptyArray<AnAction>()
    return DiscussionsViewOption.values().map { viewOption -> ToggleOptionAction(model, viewOption) }.toTypedArray()
  }

  private class ToggleOptionAction(
    private val model: CombinedDiffModel,
    private val viewOption: DiscussionsViewOption
  ) : ToggleAction(viewOption.toActionName()) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = e.getData(GHPRDiffReviewSupport.DATA_KEY) != null
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val diffReviewSupport: GHPRDiffReviewSupport = e.getData(GHPRDiffReviewSupport.DATA_KEY) ?: return false
      return diffReviewSupport.discussionsViewOption == viewOption
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      model.getLoadedRequests().forEach { request ->
        request.getUserData(GHPRDiffReviewSupport.KEY)?.discussionsViewOption = viewOption
      }
    }
  }
}
