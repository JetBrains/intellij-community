// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.CommonBundle
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.EditableComponentFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindDisabled
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineUIUtil.createNoteTitleComponent
import javax.swing.JComponent

object GitLabNoteComponentFactory {

  fun create(componentType: ComponentType,
             project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             vm: GitLabNoteViewModel): JComponent {
    val textPanel = createTextPanel(cs, vm.htmlBody)

    val actionsVm = vm.actionsVm
    val contentPanel = if (actionsVm != null) {
      EditableComponentFactory.create(cs, textPanel, actionsVm.editVm) { editCs, editVm ->
        GitLabNoteEditorComponentFactory.create(project, editCs, editVm, createEditActionsConfig(actionsVm, editVm))
      }
    }
    else {
      textPanel
    }

    val actionsPanel = createActions(cs, flowOf(vm))
    return CodeReviewChatItemUIUtil.build(componentType,
                                          { avatarIconsProvider.getIcon(vm.author, it) },
                                          contentPanel) {
      withHeader(createNoteTitleComponent(cs, vm), actionsPanel)
    }
  }

  fun createActions(cs: CoroutineScope, note: Flow<GitLabNoteViewModel>): JComponent {
    val panel = HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      cs.launch {
        note.mapNotNull { it.actionsVm }.collectLatest {
          removeAll()
          coroutineScope {
            CodeReviewCommentUIUtil.createEditButton { _ -> it.startEditing() }.apply {
              bindDisabled(this@coroutineScope, it.busy)
            }.also(::add)
            CodeReviewCommentUIUtil.createDeleteCommentIconButton { _ -> it.delete() }.apply {
              bindDisabled(this@coroutineScope, it.busy)
            }.also(::add)
            repaint()
            revalidate()
            awaitCancellation()
          }
        }
      }
    }
    return panel
  }

  fun createTextPanel(cs: CoroutineScope, textFlow: Flow<@Nls String>): JComponent =
    SimpleHtmlPane().apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      bindText(cs, textFlow)
    }

  fun createEditActionsConfig(actionsVm: GitLabNoteAdminActionsViewModel,
                              editVm: GitLabNoteEditingViewModel): CommentInputActionsComponentFactory.Config =
    CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(swingAction(CollaborationToolsBundle.message("review.comment.save")) {
        editVm.submit()
      }),
      cancelAction = MutableStateFlow(swingAction(CommonBundle.getCancelButtonText()) {
        actionsVm.stopEditing()
      }),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comment.save.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )
}