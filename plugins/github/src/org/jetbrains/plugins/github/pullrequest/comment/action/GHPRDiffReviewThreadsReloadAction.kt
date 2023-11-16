// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel

class GHPRDiffReviewThreadsReloadAction
  : RefreshAction({ GithubBundle.message("pull.request.review.refresh.data.task") },
                  { GithubBundle.message("pull.request.review.refresh.data.task.description") },
                  AllIcons.Actions.Refresh) {

  override fun update(e: AnActionEvent) {
    val vm = e.getData(GHPRDiffViewModel.DATA_KEY)
    e.presentation.isVisible = vm != null
    e.presentation.isEnabled = vm?.isLoadingReviewData?.value?.not() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GHPRDiffViewModel.DATA_KEY).reloadReview()
  }
}