// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.comment

import com.intellij.collaboration.ui.codereview.timeline.thread.CodeReviewResolvableItemViewModel
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider

interface GHPRReviewThreadViewModel : CodeReviewResolvableItemViewModel {
  val avatarIconsProvider: GHAvatarIconsProvider

  val id: String

  val canCreateReplies: StateFlow<Boolean>
  val newReplyVm: GHPRNewThreadCommentViewModel
}