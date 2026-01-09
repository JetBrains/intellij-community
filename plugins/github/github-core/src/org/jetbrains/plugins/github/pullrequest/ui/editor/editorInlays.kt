// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor


import com.intellij.collaboration.ui.codereview.editor.CodeReviewActiveRangesTracker
import com.intellij.collaboration.ui.codereview.editor.CodeReviewComponentInlayRenderer
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.collaboration.util.Hideable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel
import javax.swing.Icon

internal sealed interface GHPREditorMappedComponentModel : CodeReviewInlayModel.Ranged {

  abstract class Thread<VM : GHPRCompactReviewThreadViewModel>(val vm: VM) : GHPREditorMappedComponentModel, Hideable {
    final override val key: Any = vm.id
    final override val hiddenState = MutableStateFlow(false)

    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }
  }

  abstract class NewComment<VM : GHPRReviewNewCommentEditorViewModel>(val vm: VM)
    : GHPREditorMappedComponentModel, CodeReviewInlayModel.Ranged.Adjustable

  abstract class AIComment(val vm: GHPRAICommentViewModel) : GHPREditorMappedComponentModel, Hideable {
    final override val key: Any = vm.key
    final override val hiddenState = MutableStateFlow(false)

    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }
  }
}

internal fun CoroutineScope.createRenderer(model: GHPREditorMappedComponentModel, userIcon: Icon, activeRangesTracker: CodeReviewActiveRangesTracker): CodeReviewComponentInlayRenderer =
  when (model) {
    is GHPREditorMappedComponentModel.Thread<*> -> GHPRReviewThreadEditorInlayRenderer(this, model, activeRangesTracker)
    is GHPREditorMappedComponentModel.NewComment<*> -> GHPRNewCommentEditorInlayRenderer(this, model, activeRangesTracker)
    is GHPREditorMappedComponentModel.AIComment -> GHPRAICommentEditorInlayRenderer(userIcon, model.vm)
  }