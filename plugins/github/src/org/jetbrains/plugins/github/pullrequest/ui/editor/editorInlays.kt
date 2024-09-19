// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.collaboration.util.Hideable
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.github.ai.GHPRAICommentViewModel
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel

sealed interface GHPREditorMappedComponentModel : CodeReviewInlayModel {
  sealed interface Editor : GHPREditorMappedComponentModel
  sealed interface Diff : GHPREditorMappedComponentModel

  abstract class Thread<VM : GHPRCompactReviewThreadViewModel>(val vm: VM)
    : GHPREditorMappedComponentModel, Editor, Diff, Hideable {
    final override val key: Any = vm.id
    final override val hiddenState = MutableStateFlow(false)
    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }
  }

  abstract class NewComment<VM : GHPRReviewNewCommentEditorViewModel>(val vm: VM)
    : GHPREditorMappedComponentModel, Editor, Diff

  abstract class AIComment(val vm: GHPRAICommentViewModel)
    : GHPREditorMappedComponentModel, Diff, Hideable {
    final override val key: Any = vm.key
    final override val hiddenState = MutableStateFlow(false)
    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }
  }
}