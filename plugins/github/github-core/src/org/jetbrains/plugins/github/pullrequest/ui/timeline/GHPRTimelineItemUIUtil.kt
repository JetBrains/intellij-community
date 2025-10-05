// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType.FULL
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.build
import com.intellij.collaboration.ui.codereview.CodeReviewTimelineUIUtil
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.collaboration.ui.setHtmlBody
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.ui.icons.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.addGithubHyperlinkListener
import java.util.*
import javax.swing.JComponent

internal object GHPRTimelineItemUIUtil {
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
      withHeader(CodeReviewTimelineUIUtil.createTitleTextPane(actor.getPresentableName(), actor.url, date), actionsPanel)
      iconTooltip = actor.getPresentableName()
    }

  //language=HTML
  fun createDescriptionComponent(text: @Nls String, type: StatusMessageType = StatusMessageType.INFO, prLinkHandler: (Long) -> Unit): JComponent {
    val textPane = SimpleHtmlPane(addBrowserListener = false).apply {
      addGithubHyperlinkListener(prLinkHandler)
      setHtmlBody(text)
    }
    return StatusMessageComponentFactory.create(textPane, type)
  }
}
