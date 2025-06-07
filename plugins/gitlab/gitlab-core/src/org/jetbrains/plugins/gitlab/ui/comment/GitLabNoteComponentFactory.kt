// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.bindDisabledIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.openapi.project.Project
import com.intellij.util.ui.InlineIconButton
import icons.CollaborationToolsIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsPickerComponentFactory
import org.jetbrains.plugins.gitlab.mergerequest.ui.emoji.GitLabReactionsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.addGitLabHyperlinkListener
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import java.awt.event.ActionListener
import java.net.URL
import javax.swing.JComponent

internal object GitLabNoteComponentFactory {

  fun create(componentType: ComponentType,
             project: Project,
             cs: CoroutineScope,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
             vm: GitLabNoteViewModel,
             place: GitLabStatistics.MergeRequestNoteActionPlace): JComponent {
    val textPanel = createTextPanel(project, cs, vm.bodyHtml, vm.serverUrl).let { panel ->
      val actionsVm = vm.actionsVm ?: return@let panel
      EditableComponentFactory.wrapTextComponent(cs, panel, actionsVm.editVm) {
        GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.UPDATE_NOTE, place)
      }
    }
    val contentPanel = VerticalListPanel(gap = CodeReviewTimelineUIUtil.VERTICAL_GAP).apply {
      add(textPanel)
      vm.reactionsVm?.let { reactionsVm ->
        add(GitLabReactionsComponentFactory.create(cs, reactionsVm))
      }
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
      add(CodeReviewTimelineUIUtil.createTitleTextPane(vm.author.name, vm.author.webUrl, vm.createdAt))

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
      cs.launchNow {
        note.collectScoped {
          try {
            val buttonsCs = this

            val actionVm = it.actionsVm
            if (actionVm != null) {
              CodeReviewCommentUIUtil.createEditButton { _ -> actionVm.startEditing() }.apply {
                isEnabled = false
                if (actionVm.canEdit()) {
                  bindDisabledIn(buttonsCs, actionVm.busy)
                }
              }.also(::add)
              CodeReviewCommentUIUtil.createDeleteCommentIconButton { _ ->
                actionVm.delete()
                GitLabStatistics.logMrActionExecuted(project, GitLabStatistics.MergeRequestAction.DELETE_NOTE, place)
              }.apply {
                bindDisabledIn(buttonsCs, actionVm.busy)
              }.also(::add)
            }

            val reactionsVm = it.reactionsVm
            if (reactionsVm != null) {
              createAddReactionButton(buttonsCs, reactionsVm).also(::add)
            }

            revalidate()
            repaint()
            awaitCancellation()
          }
          finally {
            removeAll()
          }
        }
      }
    }
    return panel
  }

  private fun createAddReactionButton(cs: CoroutineScope, reactionsVm: GitLabReactionsViewModel): InlineIconButton {
    val button = InlineIconButton(
      CollaborationToolsIcons.AddEmoji,
      CollaborationToolsIcons.AddEmojiHovered,
      tooltip = CollaborationToolsBundle.message("review.comments.reaction.add.tooltip")
    )
    button.actionListener = ActionListener {
      cs.launch {
        GitLabReactionsPickerComponentFactory.showPopup(reactionsVm, button)
      }
    }

    return button
  }

  fun createTextPanel(project: Project, cs: CoroutineScope, textFlow: Flow<@Nls String>, baseUrl: URL): JComponent =
    SimpleHtmlPane(baseUrl = baseUrl, addBrowserListener = false).apply {
      bindTextIn(cs, textFlow)
      addGitLabHyperlinkListener(project)
    }
}