// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.ui.EditableComponentFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.createEditActionsConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import javax.swing.JComponent


object GitLabCodeReviewCommentTextFieldFactory {
  fun createIn(
    cs: CoroutineScope,
    vm: CodeReviewSubmittableTextViewModel,
    actions: CommentInputActionsComponentFactory.Config,
    icon: CommentTextFieldFactory.IconConfig? = null,
  ): JComponent {
    return CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon)
  }
}

object GitLabEditableComponentFactory {
  fun wrapTextComponent(cs: CoroutineScope, component: JComponent, editVmFlow: Flow<CodeReviewTextEditingViewModel?>,
                        afterSave: () -> Unit = {}): JComponent =
    EditableComponentFactory.create(cs, component, editVmFlow) { editVm ->
      val actions = createEditActionsConfig(editVm, afterSave)
      GitLabCodeReviewCommentTextFieldFactory.createIn(cs, editVm, actions)
    }
}

