// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.TransparentScrollPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.*
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestTimelineItem
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel.LoadingState
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel

object GitLabMergeRequestTimelineComponentFactory {
  fun create(cs: CoroutineScope,
             vm: GitLabMergeRequestTimelineViewModel,
             avatarIconsProvider: IconsProvider<GitLabUserDTO>): JComponent {
    val panel = Wrapper().apply {
      isOpaque = true
      CollaborationToolsUIUtil.overrideUIDependentProperty(this) {
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }
    }

    cs.launch {
      vm.timelineLoadingFlow.map { state ->
        when (state) {
          LoadingState.Loading -> {
            JLabel(AnimatedIcon.Default())
          }
          is LoadingState.Error -> {
            SimpleHtmlPane(state.exception.localizedMessage)
          }
          is LoadingState.Result -> {
            VerticalListPanel(0).apply {
              for (item in state.items) {
                add(createItemComponent(avatarIconsProvider, item))
              }
            }.let {
              TransparentScrollPane(it)
            }
          }
          else -> null
        }
      }.collect {
        panel.setContent(it)
        panel.repaint()
      }
    }

    return panel.also {
      UiNotifyConnector.doWhenFirstShown(it) {
        vm.startLoading()
      }
    }
  }

  private fun createItemComponent(avatarIconsProvider: IconsProvider<GitLabUserDTO>, item: GitLabMergeRequestTimelineItem): JComponent {
    val content = createContent(item)

    return CodeReviewChatItemUIUtil.build(CodeReviewChatItemUIUtil.ComponentType.FULL,
                                          { avatarIconsProvider.getIcon(item.actor, it) },
                                          content) {
      withHeader(createTitleTextPane(item.actor, item.date))
    }
  }

  private fun createContent(item: GitLabMergeRequestTimelineItem): JComponent {
    return when (item) {
      is GitLabMergeRequestTimelineItem.Discussion -> createDiscussionContent(item)
      is GitLabMergeRequestTimelineItem.LabelEvent -> createLabeledEventContent(item)
      is GitLabMergeRequestTimelineItem.MilestoneEvent -> createMilestonedEventContent(item)
      is GitLabMergeRequestTimelineItem.StateEvent -> createStateChangeContent(item)
    }
  }

  private fun createDiscussionContent(item: GitLabMergeRequestTimelineItem.Discussion): JComponent {
    val firstNote = item.discussion.notes.first()
    return if (firstNote.system) {
      StatusMessageComponentFactory.create(SimpleHtmlPane(firstNote.body))
    }
    else {
      SimpleHtmlPane(firstNote.body)
    }
  }

  private fun createLabeledEventContent(item: GitLabMergeRequestTimelineItem.LabelEvent): JComponent {
    val text = when (item.event.actionEnum) {
      GitLabResourceLabelEventDTO.Action.ADD -> GitLabBundle.message("merge.request.event.label.added", item.event.label.toHtml())
      GitLabResourceLabelEventDTO.Action.REMOVE -> GitLabBundle.message("merge.request.event.label.removed", item.event.label.toHtml())
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


  private fun createTitleTextPane(actor: GitLabUserDTO, date: Date?): JComponent {
    val userNameLink = HtmlChunk.link(actor.webUrl, actor.name)
      .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(UIUtil.getLabelForeground())))
      .bold()
    val titleText = HtmlBuilder()
      .append(userNameLink)
      .append(HtmlChunk.nbsp())
      .apply {
        if (date != null) {
          append(JBDateFormat.getFormatter().formatPrettyDateTime(date))
        }
      }.toString()
    val titleTextPane = SimpleHtmlPane(titleText).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return titleTextPane
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
