// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.*
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentTextFieldFactory
import com.intellij.collaboration.ui.codereview.comment.CommentInputActionsComponentFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.codereview.timeline.comment.CommentTextFieldFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindVisibilityIn
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.StyleSheetUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.addGitLabHyperlinkListener
import org.jetbrains.plugins.gitlab.ui.comment.*
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import org.jetbrains.plugins.gitlab.util.GitLabStatistics
import javax.swing.JComponent
import javax.swing.JScrollPane

internal object GitLabMergeRequestTimelineComponentFactory {
  fun create(project: Project,
             cs: CoroutineScope,
             timelineVm: GitLabMergeRequestTimelineViewModel,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val titleComponent = GitLabMergeRequestTimelineTitleComponent.create(project, cs, timelineVm).let {
      CollaborationToolsUIUtil.wrapWithLimitedSize(it, CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH)
    }.apply {
      border = Borders.empty(CodeReviewTimelineUIUtil.HEADER_VERT_PADDING, CodeReviewTimelineUIUtil.ITEM_HOR_PADDING)
    }
    val descriptionComponent = GitLabMergeRequestTimelineDescriptionComponent
      .createComponent(project, cs, timelineVm, avatarIconsProvider)

    val timelinePanel = VerticalListPanel(0)
    val errorOrTimelineComponent = createErrorOrTimelineComponent(cs, project, avatarIconsProvider, timelineVm, timelinePanel)

    val newNoteField = timelineVm.newNoteVm?.let {
      cs.createNewNoteField(project, avatarIconsProvider, it)
    }
    timelinePanel.apply {
      add(titleComponent)
      add(descriptionComponent)
      add(errorOrTimelineComponent)
      if (newNoteField != null) {
        add(newNoteField)
      }
    }

    val timelineController = object : GitLabMergeRequestTimelineController {
      override var showEvents: Boolean
        get() = timelineVm.showEvents.value
        set(value) {
          timelineVm.setShowEvents(value)
        }
    }

    DataManager.registerDataProvider(timelinePanel) { dataId ->
      when {
        GitLabMergeRequestViewModel.DATA_KEY.`is`(dataId) -> timelineVm
        GitLabMergeRequestTimelineController.DATA_KEY.`is`(dataId) -> timelineController
        else -> null
      }
    }

    return ScrollPaneFactory.createScrollPane(timelinePanel, true).apply {
      horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
      viewport.isOpaque = false
      background = JBColor.lazy {
        val scheme = EditorColorsManager.getInstance().globalScheme
        scheme.defaultBackground
      }
    }
  }

  private fun CoroutineScope.createNewNoteField(project: Project,
                                                iconsProvider: IconsProvider<GitLabUserDTO>,
                                                editVm: NewGitLabNoteViewModel): JComponent {
    val noteCs = this

    val addAction = editVm.submitActionIn(noteCs, CollaborationToolsBundle.message("review.comment.submit"),
                                          project, NewGitLabNoteType.STANDALONE, GitLabStatistics.MergeRequestNoteActionPlace.TIMELINE)
    val addAsDraftAction = editVm.submitAsDraftActionIn(noteCs, CollaborationToolsBundle.message("review.comments.save-as-draft.action"),
                                                        project, NewGitLabNoteType.STANDALONE,
                                                        GitLabStatistics.MergeRequestNoteActionPlace.TIMELINE)
    val actions = CommentInputActionsComponentFactory.Config(
      primaryAction = editVm.primarySubmitActionIn(noteCs, addAction, addAsDraftAction),
      secondaryActions = editVm.secondarySubmitActionIn(noteCs, addAction, addAsDraftAction),
      submitHint = editVm.submitActionHintIn(noteCs,
                                             CollaborationToolsBundle.message("review.comment.hint",
                                                                              CommentInputActionsComponentFactory.submitShortcutText),
                                             GitLabBundle.message("merge.request.details.action.draft.comment.hint",
                                                                  CommentInputActionsComponentFactory.submitShortcutText)
      )
    )

    val itemType = ComponentType.FULL
    val icon = CommentTextFieldFactory.IconConfig.of(itemType, iconsProvider, editVm.currentUser)

    return CodeReviewCommentTextFieldFactory.createIn(noteCs, editVm, actions, icon).apply {
      border = Borders.empty(itemType.inputPaddingInsets)
    }
  }

  private fun createErrorOrTimelineComponent(cs: CoroutineScope,
                                             project: Project,
                                             avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                             timelineVm: GitLabMergeRequestTimelineViewModel,
                                             timelinePanel: JComponent): JComponent {
    val actionManager = ActionManager.getInstance()
    val timelineActionGroup = actionManager.getAction("GitLab.Merge.Request.Timeline.Popup") as ActionGroup
    val errorActionGroup = actionManager.getAction("GitLab.Merge.Request.Timeline.Error.Popup") as ActionGroup

    val timelineOrErrorPanel = Wrapper()

    val timelineItems = MutableStateFlow<List<GitLabMergeRequestTimelineItemViewModel>>(listOf())
    val timelineItemContent = ComponentListPanelFactory.createVertical(cs, timelineItems) { item ->
      createItemComponent(project, avatarIconsProvider, item)
    }
    val timelineItemsAndLoadingLabel = VerticalListPanel(gap = 0).apply {
      val panel = this
      border = Borders.empty()

      add(timelineItemContent)
      add(LoadingLabel().apply {
        border = Borders.empty(ComponentType.FULL.paddingInsets)
        panel.launchOnShow("LoadingLabel.visibility") {
          val cs = this
          bindVisibilityIn(cs, timelineVm.isLoading)
        }
      }, ListLayout.Alignment.CENTER)
    }
    timelineOrErrorPanel.setContent(timelineItemsAndLoadingLabel)

    cs.launch(Dispatchers.Main) {
      timelineVm.timelineItems.collect {
        it.fold(
          onSuccess = { items ->
            timelineItems.emit(items)

            timelineOrErrorPanel.setContent(timelineItemsAndLoadingLabel)
            PopupHandler.installPopupMenu(timelinePanel, timelineActionGroup, ActionPlaces.POPUP)
          },
          onFailure = { exception ->
            val errorPresenter = ErrorStatusPresenter.simple<Throwable>(
              GitLabBundle.message("merge.request.timeline.error"),
              actionProvider = {
                swingAction(GitLabBundle.message("merge.request.reload")) {
                  timelineVm.reloadData()
                }
              }
            )
            val errorPanel = ErrorStatusPanelFactory.create(exception, errorPresenter)

            timelineOrErrorPanel.setContent(CollaborationToolsUIUtil.moveToCenter(errorPanel))
            PopupHandler.installPopupMenu(timelinePanel, errorActionGroup, ActionPlaces.POPUP)
          })
      }
    }

    return timelineOrErrorPanel
  }

  private fun CoroutineScope.createItemComponent(project: Project,
                                                 avatarIconsProvider: IconsProvider<GitLabUserDTO>,
                                                 item: GitLabMergeRequestTimelineItemViewModel): JComponent =
    when (item) {
      is GitLabMergeRequestTimelineItemViewModel.Immutable -> {
        val content = createContent(project, item)

        CodeReviewChatItemUIUtil.build(ComponentType.FULL,
                                       { avatarIconsProvider.getIcon(item.actor, it) },
                                       content) {
          withHeader(CodeReviewTimelineUIUtil.createTitleTextPane(item.actor.name, item.actor.webUrl, item.date))
        }
      }
      is GitLabMergeRequestTimelineItemViewModel.Discussion -> {
        GitLabMergeRequestTimelineDiscussionComponentFactory.createIn(project, this, item, avatarIconsProvider)
      }
      is GitLabMergeRequestTimelineItemViewModel.DraftNote -> {
        GitLabMergeRequestTimelineDiscussionComponentFactory.createIn(project, this, item, avatarIconsProvider)
      }
    }

  private fun createContent(project: Project, item: GitLabMergeRequestTimelineItemViewModel.Immutable): JComponent =
    when (item) {
      is GitLabMergeRequestTimelineItemViewModel.SystemNote -> createSystemDiscussionContent(project, item)
      is GitLabMergeRequestTimelineItemViewModel.LabelEvent -> createLabeledEventContent(item)
      is GitLabMergeRequestTimelineItemViewModel.MilestoneEvent -> createMilestonedEventContent(item)
      is GitLabMergeRequestTimelineItemViewModel.StateEvent -> createStateChangeContent(item)
    }

  private fun createSystemDiscussionContent(project: Project,
                                            item: GitLabMergeRequestTimelineItemViewModel.SystemNote): JComponent {
    val content = item.content
    if (content.contains("Compare with previous version")) {
      try {
        val lines = content.lines()
        val title = lines[0]
        val commits = lines[2]
        return VerticalListPanel().apply {
          add(SimpleHtmlPane(addBrowserListener = false).apply {
            setHtmlBody(title)
            addGitLabHyperlinkListener(project)
          })
          add(StatusMessageComponentFactory.create(createCommitsListPane(project, commits)))
        }
      }
      catch (e: Exception) {
        thisLogger().warn("Error occurred while parsing the note with added commits", e)
      }
    }
    return StatusMessageComponentFactory.create(SimpleHtmlPane(addBrowserListener = false).apply {
      setHtmlBody(item.contentHtml ?: "")
      addGitLabHyperlinkListener(project)
    })
  }

  private val noUlGapsStyleSheet by lazy {
    StyleSheetUtil.loadStyleSheet("""ul {margin: 0}""")
  }

  private fun createCommitsListPane(project: Project, commits: @NlsSafe String) = SimpleHtmlPane(noUlGapsStyleSheet, addBrowserListener = false).apply {
    setHtmlBody(commits)
    addGitLabHyperlinkListener(project)
  }

  private fun createLabeledEventContent(item: GitLabMergeRequestTimelineItemViewModel.LabelEvent): JComponent {
    val text = when (item.event.actionEnum) {
      GitLabResourceLabelEventDTO.Action.ADD ->
        GitLabBundle.message("merge.request.event.label.added", item.event.label?.toHtml().orEmpty())
      GitLabResourceLabelEventDTO.Action.REMOVE ->
        GitLabBundle.message("merge.request.event.label.removed", item.event.label?.toHtml().orEmpty())
    }
    val textPane = SimpleHtmlPane(text)
    return StatusMessageComponentFactory.create(textPane)
  }

  private fun createMilestonedEventContent(item: GitLabMergeRequestTimelineItemViewModel.MilestoneEvent): JComponent {
    val text = when (item.event.actionEnum) {
      GitLabResourceMilestoneEventDTO.Action.ADD ->
        GitLabBundle.message("merge.request.event.milestone.changed", item.event.milestone.toHtml())
      GitLabResourceMilestoneEventDTO.Action.REMOVE ->
        GitLabBundle.message("merge.request.event.milestone.removed", item.event.milestone.toHtml())
    }
    val textPane = SimpleHtmlPane(text)
    return StatusMessageComponentFactory.create(textPane)
  }

  private fun createStateChangeContent(item: GitLabMergeRequestTimelineItemViewModel.StateEvent): JComponent {
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
