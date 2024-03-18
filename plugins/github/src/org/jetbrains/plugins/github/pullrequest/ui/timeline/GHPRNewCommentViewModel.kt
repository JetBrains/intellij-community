// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModelBase
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.io.await
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider

class GHPRNewCommentViewModel(
  override val project: Project,
  parentCs: CoroutineScope,
  private val commentsDataProvider: GHPRCommentsDataProvider
) : CodeReviewSubmittableTextViewModelBase(project, parentCs, "") {
  fun submit() = submit {
    commentsDataProvider.addComment(EmptyProgressIndicator(), it).await()
    text.value = ""
  }
}