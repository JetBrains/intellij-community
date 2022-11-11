// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.JBDateFormat
import com.intellij.util.text.nullize
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.HtmlEditorPane
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object GHPRTimelineItemUIUtil {
  private const val MAIN_AVATAR_SIZE = 30
  private const val AVATAR_CONTENT_GAP = 14

  val maxTimelineItemTextWidth: Int
    get() = GHUIUtil.getPRTimelineWidth()

  val maxTimelineItemWidth: Int
    get() = maxTimelineItemTextWidth + JBUIScale.scale(MAIN_AVATAR_SIZE) + JBUIScale.scale(AVATAR_CONTENT_GAP)

  fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                 actor: GHActor,
                 date: Date?,
                 content: JComponent,
                 actionsPanel: JComponent? = null): JComponent {
    return createItem(avatarIconsProvider, actor, date, content, maxTimelineItemTextWidth, actionsPanel)
  }

  fun createItem(avatarIconsProvider: GHAvatarIconsProvider,
                 actor: GHActor,
                 date: Date?,
                 content: JComponent,
                 maxContentWidth: Int = maxTimelineItemTextWidth,
                 actionsPanel: JComponent? = null): JComponent {
    val icon = avatarIconsProvider.getIcon(actor.avatarUrl.nullize(), MAIN_AVATAR_SIZE)
    val iconLabel = JLabel(icon).apply {
      toolTipText = actor.login
    }

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

    return JPanel(null).apply {
      isOpaque = false
      border = JBUI.Borders.empty(10, 16)

      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))

      add(iconLabel, CC().spanY(2).alignY("top")
        .gapRight("${JBUIScale.scale(AVATAR_CONTENT_GAP)}"))

      add(titleTextPane, CC().grow().push().gapRight("push")
        .maxWidth("$maxTimelineItemTextWidth"))

      if (actionsPanel != null) {
        add(actionsPanel, CC().gapLeft("${JBUIScale.scale(10)}"))
      }
      add(content, CC().push().grow().spanX(2).newline()
        .gapTop("${JBUIScale.scale(4)}")
        .minWidth("0").maxWidth("$maxContentWidth"))
    }
  }
}