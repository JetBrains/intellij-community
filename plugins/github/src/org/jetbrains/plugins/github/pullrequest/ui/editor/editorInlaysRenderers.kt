// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.async.extensionListFlow
import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import javax.swing.Icon
import javax.swing.JComponent

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

interface GHPRAICommentComponentFactory {
  companion object {
    val EP_NAME = ExtensionPointName<GHPRAICommentComponentFactory>("intellij.vcs.github.commentComponentFactory")
  }

  fun createAICommentIn(cs: CoroutineScope, userIcon: Icon, vm: GHPRAICommentViewModel): JComponent
}

internal class GHPRAICommentEditorInlayRenderer internal constructor(cs: CoroutineScope, userIcon: Icon, vm: GHPRAICommentViewModel)
  : CodeReviewComponentInlayRenderer(Wrapper().apply {
    bindContentIn(cs, GHPRAICommentComponentFactory.EP_NAME.extensionListFlow()) { extensions ->
      val extension = extensions.firstOrNull() ?: return@bindContentIn null
      extension.createAICommentIn(cs, userIcon, vm)
    }
})
