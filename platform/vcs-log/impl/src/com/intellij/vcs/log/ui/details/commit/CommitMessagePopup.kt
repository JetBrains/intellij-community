// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.details.commit

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.OnePixelDivider
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.ListLayout.GrowPolicy
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil.CommitPresentation
import javax.swing.ScrollPaneConstants
import kotlin.math.roundToInt

private const val PREF_HEIGHT = 600

// 50em + gaps
private val prefWidth: Int
  get() = (JBUIScale.DEF_SYSTEM_FONT_SIZE * 50 + CommitDetailsPanel.SIDE_BORDER * 2).roundToInt()

internal object CommitMessagePopup {
  /**
   * Creates a resizable popup that shows the messages of [presentations] in the given order.
   * When [selectedCommitCount] is larger than the list, a label states how many commits are shown.
   * A click on a commit link closes the popup and calls [navigate].
   */
  fun createPopup(
    project: Project,
    presentations: List<CommitPresentation>,
    navigate: (CommitId) -> Unit,
    selectedCommitCount: Int = presentations.size,
  ): JBPopup {
    var popup: JBPopup? = null
    val messagePanels = presentations.map { presentation ->
      CommitMessagePanel { commitId ->
        popup?.cancel()
        navigate(commitId)
      }.apply {
        border = JBUI.Borders.empty(CommitDetailsPanel.INTERNAL_BORDER, CommitDetailsPanel.SIDE_BORDER)
        updateMessage(presentation)
      }
    }

    val content = ScrollablePanel(ListLayout.vertical(0, horGrow = GrowPolicy.GROW)).apply {
      isOpaque = false
      messagePanels.forEachIndexed { index, messagePanel ->
        if (index != 0) add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
        add(messagePanel)
      }
      if (selectedCommitCount > presentations.size) {
        add(SeparatorComponent(0, OnePixelDivider.BACKGROUND, null))
        add(JBLabel(VcsLogBundle.message("vcs.log.details.showing.selected.commits", presentations.size, selectedCommitCount)).apply {
          font = FontUtil.getCommitMetadataFont()
          border = JBUI.Borders.empty(CommitDetailsPanel.INTERNAL_BORDER, CommitDetailsPanel.SIDE_BORDER)
        })
      }
    }

    val scrollPane = ScrollPaneFactory.createScrollPane(content, true).apply {
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
      viewport.background = getCommitDetailsBackground()
      preferredSize = JBDimension(prefWidth, PREF_HEIGHT)
    }

    return JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, messagePanels.firstOrNull() ?: content)
      .setProject(project)
      .setTitle(VcsLogBundle.message("vcs.log.commit.message.popup.title"))
      .setResizable(true)
      .setMovable(true)
      .setRequestFocus(true)
      .setCancelButton(IconButton(CommonBundle.message("action.text.close"), AllIcons.Actions.Close, AllIcons.Actions.CloseHovered))
      .createPopup().also {
        popup = it
      }
  }
}
