// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.timeline

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.TransparentScrollPane
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
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
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestTimelineItem
import org.jetbrains.plugins.gitlab.mergerequest.ui.timeline.GitLabMergeRequestTimelineViewModel.LoadingState
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
    val text = when (item) {
      is GitLabMergeRequestTimelineItem.Discussion -> item.discussion.notes.first().body
      is GitLabMergeRequestTimelineItem.LabelEvent -> "${item.event.action} label ${item.event.label.name}"
      is GitLabMergeRequestTimelineItem.MilestoneEvent -> "${item.event.action} milestone ${item.event.milestone.title}"
      is GitLabMergeRequestTimelineItem.StateEvent -> "changed state to ${item.event.state}"
    }
    return SimpleHtmlPane(text)
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