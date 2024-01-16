// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.collaboration.util.Hideable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

sealed interface GHPREditorMappedComponentModel : CodeReviewInlayModel {
  abstract class Thread<VM : GHPRReviewThreadEditorViewModel>(val vm: VM)
    : GHPREditorMappedComponentModel, Hideable {
    final override val key: Any = vm.id
    protected val hidden = MutableStateFlow(false)
    final override fun toggleHidden() = hidden.update { !it }
  }

  abstract class NewComment<VM : GHPRReviewNewCommentEditorViewModel>(val vm: VM)
    : GHPREditorMappedComponentModel {
  }
}