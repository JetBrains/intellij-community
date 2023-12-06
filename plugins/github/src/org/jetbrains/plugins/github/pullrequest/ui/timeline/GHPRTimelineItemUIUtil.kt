// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType.FULL
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.build
import com.intellij.collaboration.ui.codereview.comment.CodeReviewCommentUIUtil.Title
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import java.util.*
import javax.swing.JComponent
import javax.swing.JEditorPane

internal object GHPRTimelineItemUIUtil {

  fun createTitleTextPane(actor: GHActor, date: Date?): JEditorPane {
    val userNameLink = HtmlChunk.link(actor.url, actor.getPresentableName())
      .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(UIUtil.getLabelForeground())))
      .bold()
    val titleText = HtmlBuilder()
      .append(userNameLink)
      .append(HtmlChunk.nbsp())
      .apply {
        if (date != null) {
          append(DateFormatUtil.formatPrettyDateTime(date))
        }
      }.toString()
    val titleTextPane = SimpleHtmlPane(titleText).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return titleTextPane
  }

  fun createTitlePane(actor: GHActor, date: Date?, additionalPanel: JComponent): JComponent {
    val titleTextPane = createTitleTextPane(actor, date)
    return HorizontalListPanel(Title.HORIZONTAL_GAP).apply {
      add(titleTextPane)
      add(additionalPanel)
    }
  }

  fun buildTimelineItem(avatarIconsProvider: GHAvatarIconsProvider,
                        actor: GHActor,
                        content: JComponent,
                        init: CodeReviewChatItemUIUtil.Builder.() -> Unit): JComponent =
    build(FULL, { avatarIconsProvider.getIcon(actor.avatarUrl, it) }, content) {
      iconTooltip = actor.getPresentableName()
      init()
    }

  fun createTimelineItem(avatarIconsProvider: GHAvatarIconsProvider,
                         actor: GHActor,
                         date: Date?,
                         content: JComponent,
                         actionsPanel: JComponent? = null): JComponent =
    buildTimelineItem(avatarIconsProvider, actor, content) {
      withHeader(createTitleTextPane(actor, date), actionsPanel)
      iconTooltip = actor.getPresentableName()
    }

  //language=HTML
  fun createDescriptionComponent(text: @Nls String, type: StatusMessageType = StatusMessageType.INFO): JComponent {
    val textPane = SimpleHtmlPane(text)
    return StatusMessageComponentFactory.create(textPane, type)
  }
}
