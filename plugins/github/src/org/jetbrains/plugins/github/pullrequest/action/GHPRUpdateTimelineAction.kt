// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle

class GHPRUpdateTimelineAction
  : RefreshAction({ GithubBundle.message("pull.request.timeline.refresh.action") },
                  { GithubBundle.message("pull.request.timeline.refresh.action.description") },
                  AllIcons.Actions.Refresh) {
  override fun update(e: AnActionEvent) {
    val context = e.getData(GHPRActionKeys.ACTION_DATA_CONTEXT)
    e.presentation.isEnabled = context?.pullRequestDataProvider?.timelineLoader != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataProvider = e.getRequiredData(GHPRActionKeys.ACTION_DATA_CONTEXT).pullRequestDataProvider
    if (dataProvider?.timelineLoader?.loadMore(true) != null)
      dataProvider.reviewData.resetReviewThreads()
  }
}