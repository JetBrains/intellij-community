// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.editor

import com.intellij.collaboration.ui.codereview.editor.CodeReviewInlayModel
import com.intellij.collaboration.util.Hideable
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.plugins.gitlab.ui.comment.GitLabMergeRequestDiscussionViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteViewModel
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel

internal sealed interface GitLabMergeRequestEditorMappedComponentModel : CodeReviewInlayModel {
  abstract class Discussion<VM : GitLabMergeRequestDiscussionViewModel>(val vm: VM)
    : GitLabMergeRequestEditorMappedComponentModel, Hideable {
    final override val key: Any = vm.id
    final override val hiddenState = MutableStateFlow(false)
    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }
  }

  abstract class DraftNote<VM : GitLabNoteViewModel>(val vm: VM)
    : GitLabMergeRequestEditorMappedComponentModel, Hideable {
    final override val key: Any = vm.id
    final override val hiddenState = MutableStateFlow(false)
    final override fun setHidden(hidden: Boolean) {
      hiddenState.value = hidden
    }
  }

  abstract class NewDiscussion<VM : NewGitLabNoteViewModel>(val vm: VM)
    : GitLabMergeRequestEditorMappedComponentModel {
    abstract fun cancel()
  }
}