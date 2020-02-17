// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.util.concurrent.CompletableFuture

internal class GHPRCommitMergeAction(busyStateModel: SingleValueModel<Boolean>,
                                     errorHandler: (String) -> Unit,
                                     detailsModel: SingleValueModel<GHPullRequest?>,
                                     private val project: Project,
                                     private val stateService: GHPRStateService)
  : GHPRMergeAction("Merge...", busyStateModel, errorHandler, detailsModel) {

  init {
    update()
  }

  override fun submitMergeTask(details: GHPullRequest): CompletableFuture<Unit>? {
    val dialog = GithubMergeCommitMessageDialog(project,
                                                "Merge Pull Request",
                                                "Merge pull request #${details.number}",
                                                details.title)
    if (!dialog.showAndGet()) {
      return null
    }

    return stateService.merge(EmptyProgressIndicator(), details.number, dialog.message, details.headRefOid)
  }
}