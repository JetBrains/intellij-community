// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.util.concurrent.CompletableFuture

internal class GHPRCommitMergeAction(busyStateModel: SingleValueModel<Boolean>,
                                     errorHandler: (String) -> Unit,
                                     private val detailsModel: SingleValueModel<GHPullRequestShort>,
                                     mergeabilityModel: SingleValueModel<GHPRMergeabilityState?>,
                                     private val project: Project,
                                     private val stateService: GHPRStateService)
  : GHPRMergeAction(GithubBundle.message("pull.request.merge.commit.action"), busyStateModel, errorHandler, mergeabilityModel) {

  init {
    update()
  }

  override fun submitMergeTask(mergeability: GHPRMergeabilityState): CompletableFuture<Unit>? {
    val dialog = GithubMergeCommitMessageDialog(project,
                                                GithubBundle.message("pull.request.merge.message.dialog.title"),
                                                GithubBundle.message("pull.request.merge.pull.request", mergeability.number),
                                                detailsModel.value.title)
    if (!dialog.showAndGet()) {
      return null
    }

    return stateService.merge(EmptyProgressIndicator(), mergeability, dialog.message, mergeability.headRefOid)
  }
}