// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import javax.swing.Icon

@ApiStatus.Internal
class GHPRReviewThreadEditorInlayRenderer internal constructor(cs: CoroutineScope, vm: GHPRCompactReviewThreadViewModel)
  : CodeReviewComponentInlayRenderer(
  GHPRReviewEditorComponentsFactory.createThreadIn(cs, vm)
)

@ApiStatus.Internal
class GHPRNewCommentEditorInlayRenderer internal constructor(cs: CoroutineScope, vm: GHPRReviewNewCommentEditorViewModel)
  : CodeReviewComponentInlayRenderer(
  GHPRReviewEditorComponentsFactory.createNewCommentIn(cs, vm)
)

internal class GHPRAICommentEditorInlayRenderer internal constructor(cs: CoroutineScope, userIcon: Icon, vm: GHPRAICommentViewModel)
  : CodeReviewComponentInlayRenderer(
  GHPRReviewEditorComponentsFactory.createAICommentIn(cs, userIcon, vm)
)
