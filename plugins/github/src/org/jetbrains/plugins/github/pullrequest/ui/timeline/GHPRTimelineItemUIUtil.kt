// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.collaboration.ui.JPanelWithBackground
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
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.awt.BorderLayout
import java.awt.Component
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal object GHPRTimelineItemUIUtil {
  const val MAIN_AVATAR_SIZE = 30
  const val AVATAR_CONTENT_GAP = 14
  const val TIMELINE_ICON_AND_GAP_WIDTH = MAIN_AVATAR_SIZE + AVATAR_CONTENT_GAP

  const val V_SIDE_BORDER: Int = 10
  const val H_SIDE_BORDER: Int = 16

  // 42em
  val TIMELINE_CONTENT_WIDTH = GHUIUtil.TEXT_CONTENT_WIDTH
  val TIMELINE_ITEM_WIDTH = TIMELINE_CONTENT_WIDTH + TIMELINE_ICON_AND_GAP_WIDTH + (H_SIDE_BORDER * 2)

  fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                 actor: GHActor,
                 date: Date?,
                 content: JComponent,
                 actionsPanel: JComponent? = null): JComponent {
    return createItem(avatarIconsProvider, actor, date, content, TIMELINE_CONTENT_WIDTH, actionsPanel = actionsPanel)
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
                 maxContentWidth: Int = TIMELINE_CONTENT_WIDTH,
                 actionsPanel: JComponent? = null): JComponent {
    val icon = avatarIconsProvider.getIcon(actor.avatarUrl, MAIN_AVATAR_SIZE)
    val titleTextPane = createTitleTextPane(actor, date)
    return createItem(icon, titleTextPane, content, maxContentWidth,
                      actionsPanel = actionsPanel)
  }

  fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                 actor: GHActor,
                 date: Date?,
                 content: JComponent,
                 maxContentWidth: Int = TIMELINE_CONTENT_WIDTH,
                 additionalTitle: JComponent? = null,
                 actionsPanel: JComponent? = null): JComponent {
    val icon = avatarIconsProvider.getIcon(actor.avatarUrl, MAIN_AVATAR_SIZE)
    val titleTextPane = createTitleTextPane(actor, date)
    val titlePanel = Panels.simplePanel(10, 0).addToCenter(titleTextPane).andTransparent().apply {
      if (additionalTitle != null) {
        addToRight(additionalTitle)
      }
    }

    return createItem(icon, titlePanel, content, maxContentWidth, actionsPanel)
  }

  private fun createItem(mainIcon: Icon,
                         title: JComponent,
                         content: JComponent,
                         maxContentWidth: Int = TIMELINE_CONTENT_WIDTH,
                         actionsPanel: JComponent? = null): JComponent {
    val iconLabel = JLabel(mainIcon)

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(V_SIDE_BORDER, H_SIDE_BORDER)

      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0")
                           .hideMode(3))

      add(iconLabel, CC().spanY(2).alignY("top")
        .gapRight("$AVATAR_CONTENT_GAP"))

      add(title, CC().push().split(2)
        .minWidth("0").maxWidth("$TIMELINE_CONTENT_WIDTH"))

      if (actionsPanel != null) {
        add(actionsPanel, CC().gapLeft("10:push"))
      }
      add(content, CC().push().grow().newline()
        .gapTop("4")
        .minWidth("0").maxWidth("$maxContentWidth"))
    }.let {
      actionsVisibleOnHover(it, actionsPanel)
      withHoverHighlight(it)
    }
  }

  fun actionsVisibleOnHover(comp: JComponent, actionsPanel: JComponent?) {
    if (actionsPanel != null) {
      object : HoverStateListener() {
        override fun hoverChanged(component: Component, hovered: Boolean) {
          actionsPanel.isVisible = hovered
        }
      }.apply {
        // reset hover to false
        mouseExited(comp)
      }.addTo(comp)
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