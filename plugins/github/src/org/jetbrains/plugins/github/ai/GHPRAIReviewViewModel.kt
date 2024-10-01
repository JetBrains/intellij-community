// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.ai

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import git4idea.changes.GitTextFilePatchWithHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel

/**
 * VM representing the review as done by AI.
 */
@ApiStatus.Internal
interface GHPRAIReviewViewModel {
  val isLoading: StateFlow<Boolean>

  fun showDiffFor(change: RefComparisonChange, line: Int? = null)
  fun getAICommentsForDiff(change: RefComparisonChange, diffData: GitTextFilePatchWithHistory): Flow<List<GHPRAICommentViewModel>>
}

@ApiStatus.Internal
interface GHPRAIReviewViewModelProvider {
  fun provide(project: Project, parentCs: CoroutineScope, prVm: GHPRInfoViewModel): GHPRAIReviewViewModel

  companion object {
    val EP_NAME = ExtensionPointName.create<GHPRAIReviewViewModelProvider>("intellij.vcs.github.aiReviewViewModelProvider")
  }
}
