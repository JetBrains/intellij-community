// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.async.mapScoped
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.bindEnabledIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.StyleSheetUtil
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineUIUtil.createTitleTextPane
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel.LoadingState
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteComponentFactory
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteEditingViewModel
import org.jetbrains.plugins.gitlab.ui.comment.GitLabNoteEditorComponentFactory
import org.jetbrains.plugins.gitlab.ui.comment.NewGitLabNoteViewModel
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JLabel

object GitLabMergeRequestTimelineComponentFactory {
  fun create(project: Project,
             cs: CoroutineScope,
             vm: GitLabMergeRequestTimelineViewModel,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>): JComponent {
    val timelinePanel = Wrapper()

    cs.launch {
      vm.timelineLoadingFlow.mapScoped { state ->
        when (state) {
          LoadingState.Loading -> {
            JLabel(AnimatedIcon.Default()).apply {
              border = Borders.empty(ComponentType.FULL.paddingInsets)
            }
          }
          is LoadingState.Error -> {
            SimpleHtmlPane(state.exception.localizedMessage).apply {
              border = Borders.empty(ComponentType.FULL.paddingInsets)
            }
          }
          is LoadingState.Result -> {
            createLoadedTimelineComponent(this, project, avatarIconsProvider, state)
          }
          else -> null
        }
      }.collect {
        timelinePanel.setContent(it)
        timelinePanel.repaint()
      }
    }

    val panel = VerticalListPanel(0).apply {
      add(timelinePanel)
    }

    panel.bindChildIn(cs, vm.newNoteVm, null) { editVm ->
      editVm?.let { createNewNoteField(project, avatarIconsProvider, it) }
    }

    return ScrollPaneFactory.createScrollPane(panel, true).apply {
      viewport.isOpaque = false
      CollaborationToolsUIUtil.overrideUIDependentProperty(this) {
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }
    }.also {
      UiNotifyConnector.doWhenFirstShown(it) {
        vm.requestLoad()
      }
    }
  }

  private fun createLoadedTimelineComponent(
    timelineCs: CoroutineScope,
    project: Project,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    timelineLoadingResult: LoadingState.Result
  ): JComponent {
    val mr = timelineLoadingResult.mr
    val titleComponent = GitLabMergeRequestTimelineTitleComponent.create(timelineCs, mr).let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }.apply {
      border = Borders.empty(CodeReviewTimelineUIUtil.HEADER_VERT_PADDING, CodeReviewTimelineUIUtil.ITEM_HOR_PADDING)
    }

    val descriptionComponent = GitLabMergeRequestTimelineDescriptionComponent.createComponent(timelineCs, mr, avatarIconsProvider)

    val timelineItemsComponent = ComponentListPanelFactory.createVertical(timelineCs, timelineLoadingResult.items,
                                                                          GitLabMergeRequestTimelineItemViewModel::id) { cs, item ->
      createItemComponent(project, cs, avatarIconsProvider, item)
    }

    return VerticalListPanel().apply {
      add(titleComponent)
      add(descriptionComponent)
      add(timelineItemsComponent)
    }
  }

  private fun CoroutineScope.createNewNoteField(project: Project,
                                                iconsProvider: IconsProvider<GitLabUserDTO>,
                                                editVm: NewGitLabNoteViewModel): JComponent {
    val noteCs = this
    val submitAction = swingAction(CollaborationToolsBundle.message("review.comments.reply.action")) {
      editVm.submit()
    }.apply {
      bindEnabledIn(noteCs, editVm.state.map { it != GitLabNoteEditingViewModel.SubmissionState.Loading })
    }

    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = MutableStateFlow(submitAction),
      submitHint = MutableStateFlow(CollaborationToolsBundle.message("review.comments.reply.hint",
                                                                     CommentInputActionsComponentFactory.submitShortcutText))
    )

    val itemType = ComponentType.FULL
    val icon = CommentTextFieldFactory.IconConfig.of(itemType, iconsProvider, editVm.currentUser)

    return GitLabNoteEditorComponentFactory.create(project, noteCs, editVm, actions, icon).apply {
      border = Borders.empty(itemType.inputPaddingInsets)
    }
  }

  private fun createItemComponent(project: Project,
                                  cs: CoroutineScope,
                                  avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                  item: GitLabMergeRequestTimelineItemViewModel): JComponent =
    when (item) {
      is GitLabMergeRequestTimelineItemViewModel.Immutable -> {
        val immutatebleItem = item.item
        val content = createContent(immutatebleItem)

        CodeReviewChatItemUIUtil.build(ComponentType.FULL,
                                       { avatarIconsProvider.getIcon(immutatebleItem.actor, it) },
                                       content) {
          withHeader(createTitleTextPane(immutatebleItem.actor, immutatebleItem.date))
        }
      }
      is GitLabMergeRequestTimelineDiscussionViewModel -> {
        GitLabMergeRequestTimelineDiscussionComponentFactory.create(project, cs, avatarIconsProvider, item)
      }
    }

  private fun createContent(item: GitLabMergeRequestTimelineItem.Immutable): JComponent =
    when (item) {
      is GitLabMergeRequestTimelineItem.SystemNote -> createSystemDiscussionContent(item)
      is GitLabMergeRequestTimelineItem.LabelEvent -> createLabeledEventContent(item)
      is GitLabMergeRequestTimelineItem.MilestoneEvent -> createMilestonedEventContent(item)
      is GitLabMergeRequestTimelineItem.StateEvent -> createStateChangeContent(item)
    }

  private fun createSystemDiscussionContent(item: GitLabMergeRequestTimelineItem.SystemNote): JComponent {
    val content = item.content
    if (content.contains("Compare with previous version")) {
      try {
        val lines = content.lines()
        val title = lines[0]
        val commits = lines[2]
        return VerticalListPanel().apply {
          add(SimpleHtmlPane(title))
          add(StatusMessageComponentFactory.create(createCommitsListPane(commits)))
        }
      }
      catch (e: Exception) {
        thisLogger().warn("Error occurred while parsing the note with added commits", e)
      }
    }
    return StatusMessageComponentFactory.create(SimpleHtmlPane(GitLabUIUtil.convertToHtml(content)))
  }

  private val noUlGapsStyleSheet by lazy {
    StyleSheetUtil.loadStyleSheet("""ul {margin: 0}""")
  }

  private fun createCommitsListPane(commits: @NlsSafe String) = SimpleHtmlPane(noUlGapsStyleSheet).apply {
    setHtmlBody(commits)
  }

  private fun createLabeledEventContent(item: GitLabMergeRequestTimelineItem.LabelEvent): JComponent {
    val text = when (item.event.actionEnum) {
      GitLabResourceLabelEventDTO.Action.ADD ->
        GitLabBundle.message("merge.request.event.label.added", item.event.label?.toHtml().orEmpty())
      GitLabResourceLabelEventDTO.Action.REMOVE ->
        GitLabBundle.message("merge.request.event.label.removed", item.event.label?.toHtml().orEmpty())
    }
    val textPane = SimpleHtmlPane(text)
    return StatusMessageComponentFactory.create(textPane)
  }

  private fun createMilestonedEventContent(item: GitLabMergeRequestTimelineItem.MilestoneEvent): JComponent {
    val text = when (item.event.actionEnum) {
      GitLabResourceMilestoneEventDTO.Action.ADD ->
        GitLabBundle.message("merge.request.event.milestone.changed", item.event.milestone.toHtml())
      GitLabResourceMilestoneEventDTO.Action.REMOVE ->
        GitLabBundle.message("merge.request.event.milestone.removed", item.event.milestone.toHtml())
    }
    val textPane = SimpleHtmlPane(text)
    return StatusMessageComponentFactory.create(textPane)
  }

  private fun createStateChangeContent(item: GitLabMergeRequestTimelineItem.StateEvent): JComponent {
    val text = when (item.event.stateEnum) {
      GitLabResourceStateEventDTO.State.CLOSED -> GitLabBundle.message("merge.request.event.closed")
      GitLabResourceStateEventDTO.State.REOPENED -> GitLabBundle.message("merge.request.event.reopened")
      GitLabResourceStateEventDTO.State.MERGED -> GitLabBundle.message("merge.request.event.merged")
    }
    val type: StatusMessageType = when (item.event.stateEnum) {
      GitLabResourceStateEventDTO.State.CLOSED -> StatusMessageType.SECONDARY_INFO
      GitLabResourceStateEventDTO.State.REOPENED -> StatusMessageType.INFO
      GitLabResourceStateEventDTO.State.MERGED -> StatusMessageType.SUCCESS
    }
    val textPane = SimpleHtmlPane(text)
    return StatusMessageComponentFactory.create(textPane, type)
  }
}

private fun GitLabLabelRestDTO.toHtml(): @Nls String {
  val bg = CollaborationToolsUIUtil.getLabelBackground(color)
  val fg = CollaborationToolsUIUtil.getLabelForeground(bg)

  return HtmlChunk.span("color: #${ColorUtil.toHex(fg)}; background: #${ColorUtil.toHex(bg)}")
    .child(HtmlChunk.nbsp())
    .addText(name)
    .child(HtmlChunk.nbsp())
    .toString()
}

private fun GitLabMilestoneDTO.toHtml(): @Nls String = HtmlChunk.link(webUrl, title).toString()
