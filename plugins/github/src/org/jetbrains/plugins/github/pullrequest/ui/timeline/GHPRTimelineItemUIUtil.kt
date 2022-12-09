// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.JPanelWithBackground
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.ComponentType.FULL
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.TEXT_CONTENT_WIDTH
import com.intellij.collaboration.ui.codereview.CodeReviewChatItemUIUtil.build
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageComponentFactory
import com.intellij.collaboration.ui.codereview.timeline.StatusMessageType
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.hover.HoverStateListener
import com.intellij.util.text.JBDateFormat
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Panels
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.BorderLayout
import java.awt.Component
import java.util.*
import javax.swing.JComponent

internal object GHPRTimelineItemUIUtil {
  val CONTENT_SHIFT = FULL.iconSize + FULL.iconGap

  const val V_SIDE_BORDER: Int = 10
  const val H_SIDE_BORDER: Int = 16

  val TIMELINE_ITEM_WIDTH = TEXT_CONTENT_WIDTH + CONTENT_SHIFT + (H_SIDE_BORDER * 2)

  fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                 actor: GHActor,
                 date: Date?,
                 content: JComponent,
                 actionsPanel: JComponent? = null): JComponent {
    return createItem(avatarIconsProvider, actor, date, content, TEXT_CONTENT_WIDTH, actionsPanel = actionsPanel)
  }

  fun createTitleTextPane(actor: GHActor, date: Date?): HtmlEditorPane {
    val titleText = HtmlBuilder()
      .appendLink(actor.url, actor.getPresentableName())
      .append(HtmlChunk.nbsp())
      .apply {
        if (date != null) {
          append(JBDateFormat.getFormatter().formatPrettyDateTime(date))
        }
      }.toString()
    val titleTextPane = HtmlEditorPane(titleText).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return titleTextPane
  }

  fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                 actor: GHActor,
                 date: Date?,
                 content: JComponent,
                 maxContentWidth: Int = TEXT_CONTENT_WIDTH,
                 actionsPanel: JComponent? = null): JComponent {
    val titleTextPane = createTitleTextPane(actor, date)
    return createItem(avatarIconsProvider, actor, titleTextPane, content, maxContentWidth,
                      actionsPanel = actionsPanel)
  }

  fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                 actor: GHActor,
                 date: Date?,
                 content: JComponent,
                 maxContentWidth: Int = TEXT_CONTENT_WIDTH,
                 additionalTitle: JComponent? = null,
                 actionsPanel: JComponent? = null): JComponent {
    val titleTextPane = createTitleTextPane(actor, date)
    val titlePanel = Panels.simplePanel(10, 0).addToCenter(titleTextPane).andTransparent().apply {
      if (additionalTitle != null) {
        addToRight(additionalTitle)
      }
    }

    return createItem(avatarIconsProvider, actor, titlePanel, content, maxContentWidth, actionsPanel)
  }

  private fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                         actor: GHActor,
                         title: JComponent,
                         content: JComponent,
                         maxContentWidth: Int?,
                         actionsPanel: JComponent? = null): JComponent {
    return build(FULL, { avatarIconsProvider.getIcon(actor.avatarUrl, it) }, content) {
      iconTooltip = actor.getPresentableName()
      header = title to actionsPanel
      this.maxContentWidth = maxContentWidth
    }.let {
      it.border = JBUI.Borders.empty(V_SIDE_BORDER, H_SIDE_BORDER)
      withHoverHighlight(it)
    }
  }

  fun withHoverHighlight(comp: JComponent): JComponent {
    val highlighterPanel = JPanelWithBackground(BorderLayout()).apply {
      isOpaque = false
      add(comp, BorderLayout.CENTER)
    }.also {
      object : HoverStateListener() {
        override fun hoverChanged(component: Component, hovered: Boolean) {
          // TODO: extract to theme colors
          component.background = if (hovered) {
            JBColor(ColorUtil.fromHex("#D8D8D833"), ColorUtil.fromHex("#4B4B4B33"))
          }
          else {
            null
          }
        }
      }.apply {
        // reset hover to false
        mouseExited(it)
      }.addTo(it)
    }
    return highlighterPanel
  }

  //language=HTML
  fun createDescriptionComponent(text: @Nls String, type: StatusMessageType = StatusMessageType.INFO): JComponent {
    val textPane = HtmlEditorPane(text)
    return StatusMessageComponentFactory.create(textPane, type)
  }
}