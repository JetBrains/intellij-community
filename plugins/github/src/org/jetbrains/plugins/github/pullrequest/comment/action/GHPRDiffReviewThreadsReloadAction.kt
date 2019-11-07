// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport

class GHPRDiffReviewThreadsReloadAction
  : RefreshAction("Refresh Comments", "Refresh GitHub pull request review threads comments", AllIcons.Actions.Refresh) {

  override fun update(e: AnActionEvent) {
    val reviewSupport = e.getData(GHPRDiffReviewSupport.DATA_KEY)
    e.presentation.isVisible = reviewSupport != null
    e.presentation.isEnabled = reviewSupport?.isLoadingReviewThreads?.not() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getRequiredData(GHPRDiffReviewSupport.DATA_KEY).reloadReviewThreads()
  }
}