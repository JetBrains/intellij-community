// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.vcs.changes.issueLinks.IssueLinkHtmlRenderer
import com.intellij.openapi.vcs.ui.FontUtil
import com.intellij.openapi.vcs.ui.FontUtil.getCommitMessageFont
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.*
import com.intellij.vcs.log.ui.frame.CommitPanel
import com.intellij.vcs.log.ui.frame.CommitPresentationUtil
import org.jetbrains.plugins.github.api.data.GithubPullRequestDetailedWithHtml
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import kotlin.properties.Delegates

internal class GithubPullRequestDetailsPanel(private val project: Project) : Wrapper(), ComponentWithEmptyText {

  private val emptyText = object : StatusText() {
    override fun isStatusVisible() = details == null
  }

  var details: GithubPullRequestDetailedWithHtml?
    by Delegates.observable<GithubPullRequestDetailedWithHtml?>(null) { _, _, _ ->
      titlePanel.update()
      detailsPanel.update()
    }

  private val titlePanel = TitlePanel()
  private val detailsPanel = DetailsPanel()

  init {
    val panel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)).apply {
      add(titlePanel)
      add(detailsPanel)
    }
    val scrollPane = ScrollPaneFactory.createScrollPane(panel, true).apply {
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }
    setContent(scrollPane)
  }

  override fun getEmptyText() = emptyText

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    emptyText.paint(this, g)
  }

  private inner class TitlePanel : HtmlPanel() {
    init {
      border = JBUI.Borders.empty(CommitPanel.EXTERNAL_BORDER, CommitPanel.SIDE_BORDER,
                                  CommitPanel.INTERNAL_BORDER, CommitPanel.SIDE_BORDER)
    }

    override fun getBody(): String {
      return details?.let {
        var content = "<b>" +
                      FontUtil.getHtmlWithFonts(IssueLinkHtmlRenderer.formatTextWithLinks(project, it.title),
                                                Font.BOLD,
                                                getCommitMessageFont()) +
                      "</b>"
        if (it.bodyHtml.isNotEmpty()) {
          content += "<br/><br/>" + FontUtil.getHtmlWithFonts(it.bodyHtml,
                                                              Font.PLAIN,
                                                              getCommitMessageFont())
        }
        content
      } ?: ""
    }

    override fun update() {
      isVisible = details != null
      super.update()
    }

    override fun getBackground(): Color {
      return if (UIUtil.isUnderDarcula()) CommitPanel.getCommitDetailsBackground() else UIUtil.getTreeBackground()
    }

    override fun paintComponent(g: Graphics?) {
      isOpaque = !UIUtil.isUnderDarcula()
      super.paintComponent(g)
    }
  }

  private inner class DetailsPanel : HtmlPanel() {
    init {
      border = JBUI.Borders.empty(CommitPanel.INTERNAL_BORDER, CommitPanel.SIDE_BORDER)
      background = UIUtil.getPanelBackground()
    }

    override fun getBody(): String {
      return details?.let {
        val text = "#${it.number} ${it.user.login}" + CommitPresentationUtil.formatDateTime(it.createdAt.time)
        FontUtil.getHtmlWithFonts(text)
      } ?: ""
    }
  }
}