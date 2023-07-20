// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.diff.toActionName
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport

class GHPRDiffReviewThreadsToggleAction : ActionGroup(), DumbAware {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val diffReviewSupport: GHPRDiffReviewSupport? = e.getData(GHPRDiffReviewSupport.DATA_KEY)
    e.presentation.isEnabledAndVisible = diffReviewSupport != null
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return DiscussionsViewOption.values().map(::ToggleOptionAction).toTypedArray()
  }

  private class ToggleOptionAction(private val viewOption: DiscussionsViewOption) : ToggleAction(viewOption.toActionName()) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = e.getData(GHPRDiffReviewSupport.DATA_KEY) != null
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val diffReviewSupport: GHPRDiffReviewSupport = e.getData(GHPRDiffReviewSupport.DATA_KEY) ?: return false
      return diffReviewSupport.discussionsViewOption == viewOption
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val diffReviewSupport: GHPRDiffReviewSupport = e.getRequiredData(GHPRDiffReviewSupport.DATA_KEY)
      diffReviewSupport.discussionsViewOption = viewOption
    }
  }
}