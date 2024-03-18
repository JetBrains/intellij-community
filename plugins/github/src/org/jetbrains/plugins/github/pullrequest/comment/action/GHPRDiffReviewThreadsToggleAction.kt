// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.collaboration.ui.codereview.diff.action.CodeReviewDiscussionsToggleAction
import com.intellij.collaboration.ui.codereview.diff.model.CodeReviewDiscussionsViewModel
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel

class GHPRDiffReviewThreadsToggleAction : CodeReviewDiscussionsToggleAction() {
  override fun findViewModel(ctx: DataContext): CodeReviewDiscussionsViewModel? = ctx.getData(GHPRDiffViewModel.DATA_KEY)
}