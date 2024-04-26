// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import org.jetbrains.plugins.github.api.data.GHActor

interface GHPRNewThreadCommentViewModel : CodeReviewSubmittableTextViewModel {
  val currentUser: GHActor
  fun submit()
}
