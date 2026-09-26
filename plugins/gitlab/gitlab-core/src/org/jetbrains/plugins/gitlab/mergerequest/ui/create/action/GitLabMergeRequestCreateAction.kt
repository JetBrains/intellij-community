// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.create.action

import com.intellij.collaboration.async.combineAndCollect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.create.model.GitLabMergeRequestCreateViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestCreateAction(
  cs: CoroutineScope,
  private val createVm: GitLabMergeRequestCreateViewModel
) : AbstractAction(GitLabBundle.message("merge.request.create.action.create.text")) {
  init {
    cs.launch {
      combineAndCollect(
        createVm.isBusy,
        createVm.existingMergeRequest.map { it != null },
        createVm.reviewRequirementsErrorState.map { it != null }
      ) { isBusy, isReviewAlreadyExists, errors ->
        isEnabled = !isBusy && !isReviewAlreadyExists && !errors
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) {
    createVm.createMergeRequest()
  }
}