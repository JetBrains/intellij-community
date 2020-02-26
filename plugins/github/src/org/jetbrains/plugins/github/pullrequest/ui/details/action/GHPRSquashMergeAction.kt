// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.github.pullrequest.action.ui.GithubMergeCommitMessageDialog
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.successOnEdt
import java.util.concurrent.CompletableFuture

internal class GHPRSquashMergeAction(busyStateModel: SingleValueModel<Boolean>,
                                     errorHandler: (String) -> Unit,
                                     detailsModel: SingleValueModel<GHPRMergeabilityState?>,
                                     private val project: Project,
                                     private val stateService: GHPRStateService,
                                     private val dataProvider: GHPRDataProvider)
  : GHPRMergeAction("Squash and Merge...", busyStateModel, errorHandler, detailsModel) {

  init {
    update()
  }

  override fun submitMergeTask(mergeability: GHPRMergeabilityState): CompletableFuture<Unit>? = dataProvider.apiCommitsRequest.successOnEdt { commits ->
    val body = "* " + StringUtil.join(commits, { it.message }, "\n\n* ")
    val dialog = GithubMergeCommitMessageDialog(project,
                                                "Merge Pull Request",
                                                "Merge pull request #${mergeability.number}",
                                                body)
    if (!dialog.showAndGet()) {
      throw ProcessCanceledException()
    }
    dialog.message
  }.thenCompose { message ->
    stateService.squashMerge(EmptyProgressIndicator(), mergeability, message, mergeability.headRefOid)
  }
}