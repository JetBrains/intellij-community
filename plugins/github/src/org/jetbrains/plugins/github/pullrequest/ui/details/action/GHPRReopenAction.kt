// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details.action

import com.intellij.openapi.progress.EmptyProgressIndicator
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRStateService
import org.jetbrains.plugins.github.ui.util.SingleValueModel

internal class GHPRReopenAction(busyStateModel: SingleValueModel<Boolean>,
                                errorHandler: (String) -> Unit,
                                private val stateService: GHPRStateService,
                                private val pullRequestId: GHPRIdentifier)
  : GHPRStateChangeAction("Reopen", busyStateModel, errorHandler) {

  init {
    update()
  }

  override val errorPrefix = "Error occurred while reopening pull request:"

  override fun submitTask() = stateService.reopen(EmptyProgressIndicator(), pullRequestId)
}