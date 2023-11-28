// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.EditableComponentFactory
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineUIUtil
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.net.URL
import javax.swing.JComponent

internal object GitLabNoteComponentFactory {

  fun create(componentType: ComponentType,
             project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             vm: GitLabNoteViewModel,
             place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    val textPanel = createTextPanel(cs, vm.bodyHtml, vm.serverUrl)

    val actionsVm = vm.actionsVm
    val contentPanel = if (actionsVm != null) {
      EditableComponentFactory.wrapTextComponent(cs, textPanel, actionsVm.editVm) {
        GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UPDATE_NOTE, place)
      }
    }
    else {
      textPanel
    }

    val actionsPanel = createActions(cs, flowOf(vm), project, place)
    return CodeReviewChatItemUIUtil.build(componentType,
                                          { avatarIconsProvider.getIcon(vm.author, it) },
                                          contentPanel) {
      withHeader(createTitle(cs, vm, project, place), actionsPanel)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun createTitle(cs: CoroutineScope, vm: GitLabNoteViewModel,
                  project: Project, place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    return HorizontalListPanel(CodeReviewCommentUIUtil.Title.HORIZONTAL_GAP).apply {
      add(GitLabMergeRequestTimelineUIUtil.createTitleTextPane(vm.author, vm.createdAt))

      val resolvedFlow = vm.discussionState.flatMapLatest { it.resolved }
      bindChildIn(cs, resolvedFlow) { resolved ->
        if (resolved) {
          CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.resolved.tag"))
        }
        else {
          null
        }
      }
      val outdatedFlow = vm.discussionState.flatMapLatest { it.outdated }
      bindChildIn(cs, outdatedFlow) { resolved ->
        if (resolved) {
          CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.outdated.tag"))
        }
        else {
          null
        }
      }

      val actionsVm = vm.actionsVm
      if (vm.isDraft && actionsVm != null) {
        add(CodeReviewCommentUIUtil.createPostNowButton { _ ->
          actionsVm.submitDraft()
          GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.POST_DRAFT_NOTE, place)
        }.apply {
          isVisible = actionsVm.canSubmit()
          if (actionsVm.canSubmit()) {
            bindDisabledIn(cs, actionsVm.busy)
          }
        })
      }
      if (vm.isDraft) {
        add(CollaborationToolsUIUtil.createTagLabel(CollaborationToolsBundle.message("review.thread.pending.tag")))
      }
    }
  }

  fun createActions(cs: CoroutineScope, note: Flow<GitLabNoteViewModel>,
                    project: Project, place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    val panel = HorizontalListPanel(CodeReviewCommentUIUtil.Actions.HORIZONTAL_GAP).apply {
      cs.launch {
        note.mapNotNull { it.actionsVm }.collectLatest {
          removeAll()
          coroutineScope {
            CodeReviewCommentUIUtil.createEditButton { _ -> it.startEditing() }.apply {
              isEnabled = false
              if (it.canEdit()) {
                bindDisabledIn(this@coroutineScope, it.busy)
              }
            }.also(::add)
            CodeReviewCommentUIUtil.createDeleteCommentIconButton { _ ->
              it.delete()
              GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.DELETE_NOTE, place)
            }.apply {
              bindDisabledIn(this@coroutineScope, it.busy)
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

  fun createTextPanel(cs: CoroutineScope, textFlow: Flow<@Nls String>, baseUrl: URL): JComponent =
    SimpleHtmlPane(baseUrl = baseUrl).apply {
      putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
      bindTextIn(cs, textFlow)
    }
}