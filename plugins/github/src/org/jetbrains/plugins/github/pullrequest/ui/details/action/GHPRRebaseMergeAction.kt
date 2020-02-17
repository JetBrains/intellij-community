// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import java.util.concurrent.CompletableFuture

internal class GHPRRebaseMergeAction(busyStateModel: SingleValueModel<Boolean>,
                                     errorHandler: (String) -> Unit,
                                     detailsModel: SingleValueModel<GHPullRequest?>,
                                     private val stateService: GHPRStateService)
  : GHPRMergeAction("Rebase and Merge", busyStateModel, errorHandler, detailsModel) {

  init {
    update()
  }

  override fun submitMergeTask(details: GHPullRequest): CompletableFuture<Unit>? =
    stateService.rebaseMerge(EmptyProgressIndicator(), details.number, details.headRefOid)
}