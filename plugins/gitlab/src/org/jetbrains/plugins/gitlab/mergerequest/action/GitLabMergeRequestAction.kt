// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.util.NlsActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import javax.swing.AbstractAction

internal sealed class GitLabMergeRequestAction(
  actionName: @NlsActions.ActionText String,
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(actionName) {
  init {
    scope.launch {
      reviewFlowVm.isBusy.collect {
        update()
      }
    }
  }

  protected abstract fun enableCondition(): Boolean

  protected fun update() {
    isEnabled = !reviewFlowVm.isBusy.value && enableCondition()
  }
}