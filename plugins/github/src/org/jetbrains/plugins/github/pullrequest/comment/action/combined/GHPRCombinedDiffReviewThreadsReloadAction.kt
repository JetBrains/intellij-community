// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.action.combined

import com.intellij.diff.tools.combined.CombinedDiffModel
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport

internal class GHPRCombinedDiffReviewThreadsReloadAction(private val model: CombinedDiffModel)
  : RefreshAction({ GithubBundle.message("pull.request.review.refresh.data.task") },
                  { GithubBundle.message("pull.request.review.refresh.data.task.description") },
                  AllIcons.Actions.Refresh) {

  override fun update(e: AnActionEvent) {
    val reviewSupports =
      model.getLoadedRequests().mapNotNull { it.getUserData(GHPRDiffReviewSupport.KEY) }

    e.presentation.isVisible = reviewSupports.isNotEmpty()
    e.presentation.isEnabled = reviewSupports.all { it.isLoadingReviewData.not() }
  }

  override fun actionPerformed(e: AnActionEvent) {
    model.getLoadedRequests().forEach { request -> request.getUserData(GHPRDiffReviewSupport.KEY)?.reloadReviewData() }
  }
}
