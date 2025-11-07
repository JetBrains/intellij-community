// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.ui.EditableComponentFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CodeReviewSubmittableTextViewModel
import com.intellij.collaboration.ui.codereview.comment.CodeReviewTextEditingViewModel
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.comment.createEditActionsConfig
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent


object GitLabCodeReviewCommentTextFieldFactory {

  fun createIn(
    cs: CoroutineScope,
    vm: CodeReviewSubmittableTextViewModel,
    actions: CommentInputActionsComponentFactory.Config,
    icon: CommentTextFieldFactory.IconConfig? = null,
  ): JComponent {

    val uploadFileAction = object : AnAction(GitLabBundle.message("action.GitLab.Review.Upload.File.text"),
                                             GitLabBundle.message("action.GitLab.Review.Upload.File.description"),
                                             AllIcons.Actions.Upload) {
      override fun actionPerformed(e: AnActionEvent) {
        TODO("Not yet implemented")
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = Registry.`is`("gitlab.merge.requests.file.upload.enabled")
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    }

    return CodeReviewCommentTextFieldFactory.createIn(cs, vm, actions, icon) { editor: Editor ->

      (editor as EditorEx).installPopupHandler(object : ContextMenuPopupHandler() {
        override fun getActionGroup(event: EditorMouseEvent): ActionGroup {
          return DefaultActionGroup(ActionManager.getInstance().getAction(IdeActions.GROUP_BASIC_EDITOR_POPUP),
                                    uploadFileAction)
        }
      })
    }
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

